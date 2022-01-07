(ns mzero.ai.ann.network
  "Specs of neurons, dimensions, weights, inputs, outputs,
  layers. Weights & layer generation fns : `new-layers`,
  `append-layer`. Layer info : `layer-data`"
  (:require [clojure.data.generators :as g]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(def max-test-dimension 100)

(def max-layer-depth 100)

(def max-dimension (* 8 1024))

;; dimension at least 2 to allow for a weight and a bias
(s/def ::dimension (-> (s/int-in 2 (inc max-dimension))
                       (s/with-gen #(s/gen (s/int-in 1 max-test-dimension)))))

;; inequality required to deal with infinity cases
;; otherwise exception "value out of range for float: Infinity"
;; because for some reason (float ##Inf) throws, although
;; (float? ##Inf) returns true
(s/def ::raw-neural-value
  (s/and float? #(<= (- Float/MAX_VALUE) % Float/MAX_VALUE)))

(s/def ::neural-value (s/and float? #(<= 0.0 % 1.0)))

;; in order to avoid costly checks in instrumented tests, especially
;; for spec'd functions taking `row-of` arguments, the size of
;; generated output must be limited. `gen-max` does not work to do
;; this, because `max-count` overrides it
(defn- row-of
  [pred]
  (-> (s/every pred
               :kind vector?
               :min-count 1
               :max-count max-dimension)
      (s/with-gen #(s/gen (s/every pred
                                   :kind vector?
                                   :min-count 1
                                   :max-count max-test-dimension)))))

(s/def ::weight-row (row-of float?))

(s/def ::weights (-> (row-of ::weight-row)))

;; Layer specs & generation
;;;;;

;; Theoretically, a layer's output vector should be spec'ed like an
;; input (bounded by 0.0 and 1.00) except for the last layer, whose
;; output vector may not have gone through an activation function (so
;; any float value goes). So, output vector is unconstrained, but
;; since for intermediate layers, outputs are inputs of the next
;; layer, intermediate outputs are indeed indirectly spec'ed as
;; between 0.0 and 1.0

(s/def ::inputs (-> (row-of ::neural-value)))

;; outputs before use of activation function
(s/def ::raw-outputs (row-of ::raw-neural-value))

(s/def ::outputs ::inputs)

(s/def ::raw-output-gradients ::raw-outputs)

(s/def ::layer
  (-> (s/keys :req [::weights ::inputs ::outputs ::raw-outputs ::raw-output-gradients])
      (s/and
       (fn [{:keys [::weights ::inputs ::outputs ::raw-outputs ::raw-output-gradients]}]
         (comment "Dimensions fit")
         (and (= (count inputs) (count weights))
              (= (count (first weights))
                 (count raw-outputs)
                 (count outputs)
                 (count raw-output-gradients)))))))

(s/def ::layers
  (-> (s/every ::layer :max-count max-layer-depth)
      (s/and
       (fn [layers]
         (comment "Each layer's output is the next layer's input")
         (reduce #(if (identical? (-> %1 ::outputs) (-> %2 ::inputs)) %2 false)
                 layers)))))

(defn- new-unplugged-layer
  [m n weights-fn seed]
  (hash-map ::inputs nil
            ::weights (weights-fn m n seed)
            ::raw-outputs (vec (repeat n 0.0))
            ::raw-output-gradients (vec (repeat n 0.0))
            ::outputs (vec (repeat n 0.0))))

(defn append-layer
  [layers new-dim weights-fn seed]
  (-> (new-unplugged-layer (count (::outputs (last layers))) new-dim
                           weights-fn seed)
      (assoc ::inputs (::outputs (last layers)))
      (#(conj layers %))))

(def weights-generating-fn-spec
  (-> (s/fspec :args (s/cat :m ::dimension :n ::dimension :seed int?)
               :ret ::weights
               :fn (fn [{{:keys [m n]} :args, :keys [ret]}]
                     (and (= m (count ret)) (= n (count (first ret))))))
      (s/with-gen
        #(gen/return
          (fn [m n s]
            (binding [g/*rnd* (java.util.Random. s)]
              (vec (repeatedly m (comp vec (partial repeatedly n g/float))))))))))

(s/fdef new-layers
  :args (s/cat :dimensions (s/every ::dimension :min-count 2)
               :weights-fn weights-generating-fn-spec
               :seed int?)
  :ret ::layers
  :fn (fn [{{:keys [dimensions]} :args, layers :ret}]
        (comment "Generated network dimensions fit supplied arguments")
        (every? (fn [[dim {:keys [::inputs]}]] (= (count inputs) dim))
                (map vector dimensions layers))))

(defn clear-weights
  "Reset all weights to 0 for the neuron represented by `col-idx` in
  matrix `weights`"
  [weights col-idx]
  (let [nb-rows (count weights)
        clear-weight-on-row
        (fn [weights_ row-idx]
          (assoc-in weights_ [row-idx col-idx] 0.0))]
    (reduce clear-weight-on-row weights (range nb-rows))))

(defn new-layers
  "Initialize a network with connected layers.
  
  `dimensions` is the list of number of units in the layers; elt 0 is
  the input dimension, elt 1 the number of units in the first layer,
  etc.  `weights-fn` creates new matrices of size `m`*`n` (it 2 first
  args) with the given `seed` (3rd arg of weights-fn, and
  coincidentally of new-layers too)."
  [dimensions weights-fn seed]
  {:pre [(>= (count dimensions) 2) (every? #(s/valid? ::dimension %) dimensions)]}
  (let [[input-dim first-dim & next-dims] dimensions
        first-layer
        (-> (new-unplugged-layer input-dim first-dim weights-fn seed)
            (assoc ::inputs (vec (repeat input-dim 0.0))))
        seeds (range (inc seed) (+ (inc seed) (count next-dims)))
        append-random-init-layer
        #(append-layer %1 (first %2) weights-fn (second %2))]
    (reduce append-random-init-layer [first-layer] (map list next-dims seeds))))

(def layer-elements #{"inputs" "weight" "bias" "raw-outputs" "outputs"})

(defn split-w-b
  "Given an m0 weight matrix, split into weight & bias.

  Since m0 weight matrices include both weights and bias, they have an
  additionnal row & col : dimensions must be decreased, except for the
  last layer : if `last-layer?` is true, it's the final output, so the
  last dimension does not have a bias, and the nb of columns is not
  decreased."
  [weights last-layer?]
  (let [initial-split
        {:weight (pop weights)
         :bias (peek weights)}
        remove-last-elt-of-row
        (fn [val row-idx] (update val row-idx pop))]
    (if (not last-layer?)
      (-> initial-split
          (update :weight #(reduce remove-last-elt-of-row % (range (count %))))
          (update :bias pop))
      initial-split)))
