(ns mzero.ai.players.network
  (:require [clojure.spec.alpha :as s]
            [uncomplicate.neanderthal.random :as rnd]
            [clojure.spec.gen.alpha :as gen]
            [mzero.ai.players.common :refer [values-in? per-element-spec]]
            [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.core :as nc]))

(s/def ::neural-value (s/double-in :min 0.0 :max 1.0 :infinity? false :NaN? false))

(s/def ::weights
  (-> nc/matrix?
      (s/and #(values-in? % 0.0 Float/MAX_VALUE)
             (fn [m]
               (comment "At least a non-0 val per neuron (column)")
               (every? #(pos? (nc/asum %)) (nc/cols m))))))

(s/def ::patterns
  (-> nc/matrix?
      (s/and (per-element-spec ::neural-value))))

(s/def ::working-matrix (-> nc/matrix?
                            (s/and #(values-in? % 0.0 Float/MAX_VALUE))))

(s/def ::neural-vector (-> nc/vctr?
                           (s/and (per-element-spec ::neural-value))))

(s/def ::inputs ::neural-vector)
(s/def ::outputs ::neural-vector)

(s/def ::layer
  (-> (s/keys :req [::weights ::patterns ::inputs ::outputs ::working-matrix])
      (s/and
       (fn [{:keys [::weights ::patterns ::inputs ::outputs ::working-matrix]}]
         (comment "Dimensions fit")
         (and (= (nc/dim weights) (nc/dim patterns) (nc/dim working-matrix))
              (= (nc/mrows weights) (nc/mrows patterns) (nc/mrows working-matrix))
              (= (nc/dim inputs) (nc/mrows weights))
              (= (nc/dim outputs) (nc/ncols weights)))))))

(s/def ::layers
  (-> (s/every ::layer)
      (s/and
       (fn [layers]
         (comment "Each layer's output is the next layer's input")
         (reduce #(if (identical? (-> %1 ::outputs) (-> %2 ::inputs)) %2 false)
                 layers)))))

(def max-layer-size 10000)
(def max-test-layer-size 10)

(s/def ::layer-dimension (-> (s/int-in 1 max-layer-size)
                             (s/with-gen #(s/gen (s/int-in 1 max-test-layer-size)))))

(defn- new-unplugged-layer
  [m n init-fn]
  (hash-map ::inputs nil
            ::weights (init-fn (nn/fge m n))
            ::patterns (init-fn (nn/fge m n))
            ::working-matrix (nn/fge m n)
            ::outputs (nn/fv n)))

(defn append-layer
  [layers new-dim init-fn]
  (-> (new-unplugged-layer (nc/dim (::outputs (last layers))) new-dim init-fn)
      (assoc ::inputs (::outputs (last layers)))
      (#(conj layers %))))

(s/fdef new-layers
  ;; Custom-made test of a valid neanderthal `rng`, since there is no
  ;; available `rng?` function in the neanderthal lib
  :args (s/cat :rng (-> #(rnd/rand-uniform! % (nn/fv 1))
                        (s/with-gen #(gen/return (rnd/rng-state nn/native-float))))
               
               :dimensions (s/every ::layer-dimension :min-count 2))
  :ret ::layers
  :fn (fn [{{:keys [dimensions]} :args, layers :ret}]
        (comment "Generated network dimensions fit supplied arguments")
        (every? (fn [[dim {:keys [::inputs]}]] (= (nc/dim inputs) dim))
                (map vector dimensions layers))))

(defn new-layers
  "Initialize a network with connected layers.
  
  `rng` is the random number generator to initialize weights and
  patterns (uniformly in-between 0 & 1).  `dimensions` is the list of
  number of units in the layers; elt 0 is the input dimension, elt 1
  the number of units in the first layer, etc."
  [rng dimensions]
  {:pre [(>= (count dimensions) 2)]}
  (let [[input-dim first-dim & next-dims] dimensions
        first-layer
        (-> (new-unplugged-layer input-dim first-dim #(rnd/rand-uniform! rng %))
            (assoc ::inputs (nn/fv input-dim)))
        append-random-init-layer
        #(append-layer %1 %2 (partial rnd/rand-uniform! rng))]
    (reduce append-random-init-layer [first-layer] next-dims)))
