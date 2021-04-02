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
            [uncomplicate.neanderthal.native :as nn]))

(def motoneuron-number 4)

(s/def ::motoneurons (s/every ::mzn/neural-value :count motoneuron-number))

(defn arcreflexes-pass!
  "Copy `inputs` to last layer input so as-to enable arc-reflexes.
  
  Erases potentially computed values. This is suboptimal since a few
  neuron values are computed by the network for nothing (and it may
  have other side effects) but is guessed to be a minor issue."
  [layers inputs]
  {:pre [(< (count inputs) (nc/dim (::mzn/inputs (last layers))))]}
  (update-in layers [(dec (count layers)) ::mzn/inputs]
             #(nc/transfer! inputs %)))

(defn- col-index [direction] (.indexOf ge/directions direction))

(defn- row-index [direction]
  (let [relative-position {:up [-1 0] :left [0 -1] :down [1 0] :right [0 1]}]
    (mzs/vision-cell-index (relative-position direction))))

(defn- set-fruit-ar-weights!
  [weights direction]
  (let [set-weight! (fn [w m v] (nr/entry! w m (col-index direction) v))
        stimulating-weight 1000.0 inhibiting-weight -500.0
        set-inhibiting-weights!
        (fn [w c]
          (reduce #(set-weight! %1 (row-index %2) inhibiting-weight) w c))]
    (-> weights
        (set-weight! (row-index direction) stimulating-weight)
        (set-inhibiting-weights! (remove #{direction} ge/directions)))))

(defn- set-fruit-ar-patterns!
  [patterns direction]
  (let [fruit-val (:fruit mzs/board-cell-to-float-map)]
    (reduce 
     (fn [p dir] (nr/entry! p (row-index dir) (col-index direction) fruit-val))
     patterns
     ge/directions)))

(defn- setup-fruit-arcreflex-in-direction!
  [layer direction]
  (-> layer
      (update ::mzn/patterns set-fruit-ar-patterns! direction)
      (update ::mzn/weights set-fruit-ar-weights! direction)))

(defn setup-fruit-eating-arcreflexes!
  "Player will move to a fruit next to it: fruit pattern with strong
  weights are put on visual cells for each motoneuron, stimulating
  when the motoneuron would move to a fruit if activated, inhibiting
  if another motoneuron would do so."
  [layers]
  (update layers (dec (count layers)) ;; update last layer
          #(reduce setup-fruit-arcreflex-in-direction! % ge/directions)))

(defn plug-motoneurons
  "Plug motoneurons to the network describe by `layers`.
  Add and setup new layers to `layers` so that motoneurons are properly
  connected. Initial patterns are all 0.
  Motoneurons have a specific layer setup, see arch docs for details."
  [layers weights-fn]
  (mzn/append-layer layers
                    motoneuron-number
                    nn/fge
                    weights-fn))

(s/fdef next-direction
  :args (s/cat :rng (-> (partial instance? java.util.Random)
                        (s/with-gen #(gen/return (java.util.Random.))))
               :motoneurons ::motoneurons)
  :ret (s/or :direction ::ge/direction
             :nil nil?))

(defn next-direction
  "Next direction is chosen by picking randomly among neurons whose
  value is one, nil if no value is > 1"
  [rng motoneurons]
  (binding [g/*rnd* rng]
    (when-let [directions-indices
               (seq (keep-indexed #(when (>= %2 1.0) %1) motoneurons))]
      (nth ge/directions (g/rand-nth directions-indices)))))
