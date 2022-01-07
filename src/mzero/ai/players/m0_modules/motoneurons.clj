(ns mzero.ai.players.m0-modules.motoneurons
  "Module to perform actions given the network output.

  A single output value is a `motoneuron`, controlling a single action
  of the player.

  The network has 5 motoneurons, one for each movement in each
  direction and one for idleness (nil). Note that motoneuron values,
  contrarily to other neurons, are unbounded : the activation function
  was not applied when computing the values."
  (:require [clojure.data.generators :as g]
            [clojure.spec.alpha :as s]
            [mzero.ai.ann.network :as mzn]
            [mzero.game.events :as ge]
            [mzero.ai.ann.ann :as mzann]))

(def movements (conj ge/directions nil))
(def motoneuron-number (count movements))

(s/def ::motoneurons (s/every ::mzn/raw-neural-value :count motoneuron-number))

(defn motoneuron-index
  "Return the index of motoneuron corresponding to `direction`, which is the position of direction in `movements`"
  [movement]
  (.indexOf movements movement))

(defn update-synapse
  "Updates pattern & weight at a single position in a layer. Accepts
  signed `srow`  & `scol` indices, -1 being converted to the last index,
  etc.

  `srow` is the index of the presynaptic neuron (in the 'input' layer),
  `scol` is the index of the postsynaptic neuron (in the 'output' layer)."
  [layer srow scol weight]
  (let [row (if (nat-int? srow) srow (+ (count (-> layer ::mzn/weights)) srow))
        col (if (nat-int? scol) scol (+ (count (first (-> layer ::mzn/weights))) scol))]
    (assoc-in layer [::mzn/weights row col] weight)))

(defn motoneurons-weights
  "Neuron weights initialized randomly with ones or minus ones (mixed).

  This weight generation fn should be used for motoneurons, who should
  not be sparsely connected to the last layer, but rather be fully
  connected. Every neuron of the last layer should have the same
  chance to contribute, positively or negatively, to a movement."
  [m n seed]
  ;; F*in neurons should be f*in properly normalized
  ;; neurons will be on average between -1, 1 somewhat 
  ;; the square root is a g*ddamn guess
  (let [unit-weight (/ 1.0 (Math/sqrt m))]
    (binding [g/*rnd* (java.util.Random. seed)]
      (->> (repeat [unit-weight (- unit-weight)]) ;; create list of -/+ values
           flatten
           (take (* m n))
           g/shuffle
           (partition n) ;; cut it in neurons
           (map vec) vec))))

(s/def motoneurons-weights mzn/weights-generating-fn-spec)

(def motoneurons-weights-with-nil-cleared
  "Weights relative to the `nil` movement are cleared since the
  `nil` movement is a baseline whose motoneuron's value should always
  be zero."
  (comp #(mzn/clear-weights % (motoneuron-index nil)) motoneurons-weights))

(defn plug-motoneurons
  "Plug motoneurons to the network describe by `layers`.
  Add and setup new layers to `layers` so that motoneurons are properly
  connected. Initial patterns are all 0.
  Motoneurons have a specific layer setup, see arch docs for details."
  [layers seed]
  (mzn/append-layer layers
                    motoneuron-number
                    motoneurons-weights-with-nil-cleared
                    seed))

(defn motoneuron-values [player]
  (-> player :ann-impl mzann/output first))
