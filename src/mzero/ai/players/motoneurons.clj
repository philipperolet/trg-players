(ns mzero.ai.players.motoneurons
  "Module to perform actions given the network output.

  A single output value is a `motoneuron`, controlling a single action
  of the player.

  The network has 4 motoneurons, one for each movement in each direction.

  The main function, `next-direction`, computes the direction to move
  to given an output vector."
  (:require [clojure.spec.alpha :as s]
            [mzero.game.events :as ge]
            [clojure.data.generators :as g]
            [mzero.ai.players.network :as mzn]
            [clojure.spec.gen.alpha :as gen]
            [uncomplicate.neanderthal.core :as nc]
            [uncomplicate.neanderthal.random :as rnd]
            [uncomplicate.neanderthal.real :as nr]
            [mzero.utils.utils :as u]
            [mzero.ai.players.senses :as mzs]
            [uncomplicate.neanderthal.native :as nn]
            [mzero.ai.players.activation :as mza]))

(def motoneuron-number 4)

(s/def ::motoneurons (s/every double? :count motoneuron-number))

(defn- col-index [direction] (.indexOf ge/directions direction))

(defn- row-index [direction]
  (let [relative-position {:up [-1 0] :left [0 -1] :down [1 0] :right [0 1]}]
    (mzs/vision-cell-index (relative-position direction))))

(defn- update-synapse
  "Updates pattern & weight at a single position in a layer. Accepts
  signed `srow` & `scol` indices, -1 being converted to the last index,
  etc."
  [layer srow scol weight]
  (let [row (if (nat-int? srow) srow (+ (nc/mrows (-> layer ::mzn/weights)) srow))
        col (if (nat-int? scol) scol (+ (nc/ncols (-> layer ::mzn/weights)) scol))]
    (-> layer
        (update ::mzn/weights nr/entry! row col weight))))

(def rmr-weight "Random move reflex weight" 200.0)
(def rmr-neuron "Index of neuron used for rand move reflex" -1)

(defn- update-rmr-intermediate-layers
  [layers]
  (reduce #(update %1 %2 update-synapse rmr-neuron rmr-neuron rmr-weight)
          layers
          (range 1 (dec (count layers))))) ;; first & last layers not updated

(defn setup-random-move-reflex
  "Reflex to move randomly when player has not moved for a
  while--relying on motoception so the while length is defined by
  motoception-persistence"
    [layers]
  layers
  #_(let [update-last-layer-direction
        #(update-synapse %1 rmr-neuron (col-index %2) rmr-weight)] 
    (-> layers
        ;; Detect motoception at 0, meaning no movement has occured for a while
        (update 0 update-synapse mzs/motoception-index rmr-neuron rmr-weight)
        ;; 1-neuron chain for all intermediate-layers
        update-rmr-intermediate-layers
        ;; last layer: for each direction, urge to move
        (update (dec (count layers))
                #(reduce update-last-layer-direction % ge/directions)))))

(defn plug-motoneurons
  "Plug motoneurons to the network describe by `layers`.
  Add and setup new layers to `layers` so that motoneurons are properly
  connected. Initial patterns are all 0.
  Motoneurons have a specific layer setup, see arch docs for details."
  [layers weights-fn]
  (mzn/append-layer layers
                    motoneuron-number
                    weights-fn))

(s/fdef next-direction
  :args (s/cat :motoneurons ::motoneurons)
  :ret (s/or :direction ::ge/direction
             :nil nil?))

(defn next-direction
  "Next direction is chosen by picking the motoneuron with the largest
  value above the activation threshold. If no value is above, or if 2
  values are equal, no direction is picked."
  [motoneurons]
  (let [max-value (apply max motoneurons)]
    (when (and (< (count (filter #{max-value} motoneurons)) 2)
               (> max-value mza/s))
      (nth ge/directions (.indexOf motoneurons max-value)))))
