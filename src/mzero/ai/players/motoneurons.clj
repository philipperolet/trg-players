(ns mzero.ai.players.motoneurons
  "Module to perform actions given the network output.

  A single output value is a `motoneuron`, controlling a single action
  of the player.

  The network has 4 motoneurons, one for each movement in each
  direction. Note that motoneuron values, contrarily to other neurons,
  are unbounded : the activation function was not applied when
  computing the values.

  The main function, `next-direction`, computes the direction to move
  to given an output vector.

  The 'random move reflex' (rmr) is also implemented here, in
  `setup-random-move-reflex!`"
  (:require [clojure.spec.alpha :as s]
            [mzero.ai.players.activation :as mza]
            [mzero.ai.players.network :as mzn]
            [mzero.ai.players.senses :as mzs]
            [mzero.game.events :as ge]
            [mzero.utils.utils :as u]
            [uncomplicate.neanderthal.core :as nc]
            [uncomplicate.neanderthal.real :as nr]))

(def motoneuron-number 4)

(s/def ::motoneurons (s/every double? :count motoneuron-number))

(defn- motoneuron-index [direction] (.indexOf ge/directions direction))

(defn- row-index [direction]
  (let [relative-position {:up [-1 0] :left [0 -1] :down [1 0] :right [0 1]}]
    (mzs/vision-cell-index (relative-position direction))))

(defn- update-synapse!
  "Updates pattern & weight at a single position in a layer. Accepts
  signed `srow`  & `scol` indices, -1 being converted to the last index,
  etc.

  `srow` is the index of the presynaptic neuron (in the 'input' layer),
  `scol` is the index of the postsynaptic neuron (in the 'output' layer)."
  [layer srow scol weight]
  (let [row (if (nat-int? srow) srow (+ (nc/mrows (-> layer ::mzn/weights)) srow))
        col (if (nat-int? scol) scol (+ (nc/ncols (-> layer ::mzn/weights)) scol))]
    (-> layer
        (update ::mzn/weights nr/entry! row col weight))))

(defn- clear-weights!
  "Reset all weights to 0 for the neuron represented by `col-idx` in `layer`"
  [layer col-idx]
  (nc/scal! 0.0 (nc/col (::mzn/weights layer) col-idx)))

(def rmr-weight "Random move reflex weight" 200.0)

(defn- rmr-neural-index
  "Index of neuron used for rand move reflex of `direction` in
  `layer`. Layer parameter omited since neuron always has same index
  independently of layer."
  [direction]
  (motoneuron-index direction))


(defn- setup-rmr-intermediate-layers!
  [layers direction]
  (let [idx (rmr-neural-index direction)
        setup-intermediate-layer!
        (fn [layer]
          (clear-weights! layer idx)
          (update-synapse! layer idx idx rmr-weight))]
    (reduce #(update %1 %2 setup-intermediate-layer!)
            layers ;; first, second & last layers not updated
            (range 2 (dec (count layers)))))) 

(def first-layer-indices
  {:a1overhalf 0
   :a2overhalf 1
   :motoception-repeat 2})

(defn- setup-rmr-first-layer!
  [layer]
  (let [rmr-weights-map
        ;; see version doc. weight order : motoception, a1, a2, b
        ;; (b's index = last = -1)
        {:a1overhalf [0 1 0 -0.5] 
         :a2overhalf [0 0 1 -0.5]
         :motoception-repeat [1 0 0 0]}
        index-value-map-from-weights
        (fn [weights]
          (map (fn [i v] {:weight-index i :weight-value v})
               [mzs/motoception-index
                mzs/aleaception-index
                (inc mzs/aleaception-index)
                -1]
               weights))
        first-layer-weights
        ;; yields {:a1overhalf [{:weight-index moto-idx :weight-value -400.0} {:we...
        (u/map-map (comp index-value-map-from-weights
                         #(map (fn [w] (* rmr-weight w)) %))
                   rmr-weights-map)
        update-rmr-synapse!
        (fn [neuron-key layer index-value-map]
          (update-synapse! layer
                           (:weight-index index-value-map)
                           (first-layer-indices neuron-key)
                           (:weight-value index-value-map)))
        update-rmr-neuron!
        (fn [layer neuron-key]
          (clear-weights! layer (first-layer-indices neuron-key))
          (reduce (partial update-rmr-synapse! neuron-key)
                  layer
                  (first-layer-weights neuron-key)))]
    (reduce update-rmr-neuron! layer (keys first-layer-indices))))

(defn- setup-rmr-second-layer!
  [layer direction]
  (let [rmr-weights-map
        ;; see version doc. weight order : motoception-repeat, a1overhalf, 
        ;; a2overhalf, b (b's index = last = -1)
        {:up [-2 -1 -1 1] 
         :right [-2 1 -1 0]
         :down [-2 -1 1 0]
         :left [-2 1 1 -1]}
        index-value-map-from-weights
        (fn [weights]
          (map (fn [i v] {:weight-index i :weight-value v})
               [(:motoception-repeat first-layer-indices)
                (:a1overhalf first-layer-indices)
                (:a2overhalf first-layer-indices)
                -1]
               weights))
        first-layer-weights-for-dir
        ;; => yields {:up [{:weight-index moto-idx :weight-value -400.0} {:we...
        (u/map-map (comp index-value-map-from-weights
                         #(map (fn [w] (* rmr-weight w)) %))
                   rmr-weights-map)
        update-rmr-synapse!
        (fn [layer index-value-map]
          (update-synapse! layer
                           (:weight-index index-value-map)
                           (rmr-neural-index direction)
                           (:weight-value index-value-map)))]
    (clear-weights! layer (rmr-neural-index direction))
    (reduce update-rmr-synapse! layer (first-layer-weights-for-dir direction))))

(defn setup-random-move-reflex!
  "Reflex to move randomly when player has not moved for a
  while--relying on motoception so the while length is defined by
  motoception-persistence.

  There are actually 4 reflexes to setup, 1 for each direction.

  In the first layer, 2 neurons compute whether random senses 1 and 2
  are over 0.5. Then, using motoception, it activates a direction for
  movement that is propagated to the last layer. Description of the
  setup is on [this schema](doc/rmr-schema.jpg).

  Note : there can be mishaps in the reflex in rare cases when
  aleaception 1 and 2 are very close to 0.5, in which case the reflex
  will not push any direction. The move after, it probably will, since
  it is very unlikely to have aleaceptions very close to 0.5
  twice. This is rare and does not seem to have consequences worthy of
  more effort."
  [layers]
  (let [setup-rmr-last-layer!
        (fn [layer direction]
          (update-synapse! layer
                           (rmr-neural-index direction)
                           (rmr-neural-index direction)
                           rmr-weight))
        setup-rmr-for-direction!
        (fn [layers direction]
          (-> layers
              (update 1 setup-rmr-second-layer! direction)
              (update (dec (count layers)) setup-rmr-last-layer! direction)
              (setup-rmr-intermediate-layers! direction)))]
    (-> (reduce setup-rmr-for-direction! layers ge/directions)
        (update 0 setup-rmr-first-layer!))))

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
