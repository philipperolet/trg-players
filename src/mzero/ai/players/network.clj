(ns mzero.ai.players.network
  (:require [clojure.spec.alpha :as s]
            [uncomplicate.neanderthal.random :as rnd]
            [clojure.spec.gen.alpha :as gen]
            [mzero.ai.players.common :as mzc]
            [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.core :as nc]))

(def max-test-dimension 100)

(s/def ::dimension (-> (s/int-in 1 mzc/max-dimension)
                       (s/with-gen #(s/gen (s/int-in 1 max-test-dimension)))))
(s/def ::neural-value (s/double-in :min 0.0 :max 1.0 :infinity? false :NaN? false))

(s/def ::weights
  (-> nc/matrix?
      (s/and (mzc/per-element-spec float?)
             #(s/valid? ::dimension (nc/mrows %))
             #(s/valid? ::dimension (nc/ncols %))
             (fn [m]
               (comment "At least a non-0 val per neuron (column)")
               (every? #(pos? (nc/asum %)) (nc/cols m))))))

(s/def ::patterns
  (-> nc/matrix?
      (s/and (mzc/per-element-spec ::neural-value)
             #(s/valid? ::dimension (nc/mrows %))
             #(s/valid? ::dimension (nc/ncols %)))))

(s/def ::working-matrix (-> nc/matrix?
                            (s/and #(mzc/values-in? % 0.0 Float/MAX_VALUE)
                                   #(s/valid? ::dimension (nc/ncols %))
                                   #(s/valid? ::dimension (nc/mrows %)))))

(s/def ::neural-vector (-> nc/vctr?
                           (s/and (mzc/per-element-spec ::neural-value)
                                  #(s/valid? ::dimension (nc/dim %)))))

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

(defn- new-unplugged-layer
  [m n patterns-fn weights-fn]
  (hash-map ::inputs nil
            ::weights (weights-fn m n)
            ::patterns (patterns-fn m n)
            ::working-matrix (nn/fge m n)
            ::outputs (nn/fv n)))

(defn append-layer
  [layers new-dim patterns-fn weights-fn]
  (-> (new-unplugged-layer (nc/dim (::outputs (last layers))) new-dim
                           patterns-fn weights-fn)
      (assoc ::inputs (::outputs (last layers)))
      (#(conj layers %))))

(def matrix-generating-fn-spec
  (-> (s/fspec :args (s/cat :m ::dimension :n ::dimension)
               :ret nc/matrix?
               :fn (fn [{{:keys [m n]} :args, :keys [ret]}]
                     (and (= m (nc/mrows ret)) (= n (nc/ncols ret)))))
      (s/with-gen (fn [] (gen/return #(rnd/rand-uniform! (nn/fge %1 %2)))))))

(s/fdef new-layers
  :args (s/cat :dimensions (s/every ::dimension :min-count 2)
               :patterns-fn matrix-generating-fn-spec
               :weights-fn matrix-generating-fn-spec)
  :ret ::layers
  :fn (fn [{{:keys [dimensions]} :args, layers :ret}]
        (comment "Generated network dimensions fit supplied arguments")
        (every? (fn [[dim {:keys [::inputs]}]] (= (nc/dim inputs) dim))
                (map vector dimensions layers))))

(defn new-layers
  "Initialize a network with connected layers.
  
  `dimensions` is the list of number of units in the layers; elt 0 is
  the input dimension, elt 1 the number of units in the first layer,
  etc.  `patterns-fn` and `weights-fn` create new matrices of size
  `m`*`n` (their 2 args) "
  [dimensions patterns-fn weights-fn]
  {:pre [(>= (count dimensions) 2) (every? #(s/valid? ::dimension %) dimensions)]}
  (let [[input-dim first-dim & next-dims] dimensions
        first-layer
        (-> (new-unplugged-layer input-dim first-dim patterns-fn weights-fn)
            (assoc ::inputs (nn/fv input-dim)))
        append-random-init-layer
        #(append-layer %1 %2 patterns-fn weights-fn)]
    (reduce append-random-init-layer [first-layer] next-dims)))
