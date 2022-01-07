(ns mzero.ai.ann.initialization
  "Various initializations for weights & bias (in same matrix). `m` is
  the input layer's dimension, `n` is the output layer's dimension.

  `random-weights` generates uniform random weights in (-1.0 1.0) that
  are normalized according to the number of neurons in the
  layer. Mostly used for testing purposes, does not perform well in
  experiments up to now
  
  `almost-empty-weights` is an empty weight init with at least 1
  non-zero weight per column (the first one)

  `draft1-sparse-weights` is a basic strategy (quickly coded to be
  used at first), tailored to initial pattern-based neurons & way of
  thinking;

  `angle-sparse-weights` is more refined, using geometry to draw
  relevant weight/bias combination

  In general, inner layers' weights should be sparsified, so that
  neurons are similar to what they might be when generation starts,
  and patterns randomized so that movements vary (otherwise the same
  direction is always picked)

  However, the last layer, plugged to motoneurons, should NOT be
  sparse. This is handled in the `motoneurons` module."
  (:require [clojure.data.generators :as g]
            [clojure.spec.alpha :as s]
            [mzero.ai.ann.common :refer [scale-float transpose]]
            [mzero.ai.ann.network :as mzn]))

;; Random weights initialization
;;;;;;;;;
(defn random-weights
  [m n seed]
  (binding [g/*rnd* (java.util.Random. seed)]
    (->> (repeatedly g/float)
         (map #(scale-float % -1.0 1.0))
         (take (* m n))
         (partition n)
         (map vec) vec)))

;; Draft1-sparse initialization
;;;;;

(def max-init-activation "Cf rand-nonzero-vector" 1.0)
(def neg-weight-ratio "Negative weights average ratio" 0.2)

(defn- nonzero-weights-nb
  "Number of nonzero weights for a column of size `dim` given a range
  `flt` between 0 and 1"
  ([dim flt]
   (int (inc (* (Math/sqrt dim) (scale-float flt 0.3 1.0)))))
  ([dim] (nonzero-weights-nb dim (g/float))))


(defn- rand-nonzero-vector
  "Every neuron (= row) has about sqrt(#rows)/2 non-zero weights,
  with a random ratio of negative weights averaging to
  `neg-weight-ratio`, all initialized to the same value (in ]0,1])
  such that if all positive synapses are fully activated and none of
  the negative ones are, the neuron fires to the value of
  `max-init-activation`"
  [dim]
  (let [nzw (nonzero-weights-nb dim)

        neg-weights-nb ;; on average, nwr ratio of negative weights
        (int (Math/round (* nzw (scale-float (g/float) 0.0 (* 2 neg-weight-ratio)))))

        ones-vector
        (into (repeat neg-weights-nb -1) (repeat (- nzw neg-weights-nb) 1))

        normalization-factor (/ (- nzw neg-weights-nb) max-init-activation)]
    (->> ones-vector
         (into (repeat (- dim nzw) 0))
         (map #(/ % normalization-factor))
         g/shuffle
         vec)))



(defn draft1-sparse-weights
  "Create a sparse matrix of size `m`*`n`, aimed to be used as weights."
  [m n seed]
  (binding [g/*rnd* (java.util.Random. seed)]
    (transpose (vec (repeatedly n #(rand-nonzero-vector m))))))

(s/def draft1-sparse-weights mzn/weights-generating-fn-spec)

;; almost-empty
;;;;;

(defn almost-empty-weights
  "Empty weight init with at least 1 non-zero weight per column (the first one)"
  [m n _]
  (vec (cons (vec (repeat n 0.5)) (repeat (dec m) (vec (repeat n 0.0))))))

(s/def almost-empty-weights mzn/weights-generating-fn-spec)

;; angle-sparse
;;;;;;;;


(defn- normalized-weights
  "Compute normalized weights by
  - initializing uniformly in ]-1, 1[;
  - uniformly picking of an angle phi in [eps-phi, pi/2-eps-phi];
  - scaling the weight vector *w* by tan(phi)/||*w*||.

  Thus phi is the angle between the hyperplan defined by (x, wx+b) and
  the (x, 0) hyperplan.

  It seems more powerful to have values in the whole [0, pi/2] of this
  angle, rather than keep the weights bounded between ]-1,1[ which
  bounds the total slope to 1, see [arbre.md]"
  ([raw-weights phi]
   {:pre [(< 0 phi (/ Math/PI 2))]}
   (let [w-norm (Math/sqrt (reduce #(+ %1 (* %2 %2)) 0 raw-weights))]
     (map #(-> % (* (Math/tan phi)) (/ w-norm)) raw-weights)))
  ([nb-weights]
   (let [raw-weights (repeatedly nb-weights #(scale-float (g/float) -1.0 1.0))
         [phi-min phi-max] [(* 0.05 Math/PI 0.5) (* 0.95 Math/PI 0.5)]
         phi (scale-float (g/float) phi-min phi-max)]
     (normalized-weights raw-weights phi))))

(defn- bias
  "Pick the bias uniformly so that the neuron is not stale, i.e. there
  are both some values of input X in the [0,1] hypercube that activate
  the neuron, and some that don't.

  How this is done is described
  in [doc/initialisation-angle-sparse.pdf]
  and [arbre.md->plasticité->Initialisation]"
  ([shuffled-normalized-weights Jp1 K init-value]
   {:pre [(<= Jp1 (count shuffled-normalized-weights))
          (or (= 0.0 K) (= 1.0 K))
          (< 0 init-value 1)]}
   (let [mu 0.1
         [wi & wjs] (take Jp1 shuffled-normalized-weights)
         sum_wj (apply + wjs)]
     (-> (scale-float init-value mu (- 1 mu))
         (* (- wi))
         (+ K (- sum_wj)))))
  ([normalized-weights]
   (let [Jp1 (g/uniform 1 (inc (count normalized-weights)))
         K (float (g/uniform 0 2))]
     (bias (g/shuffle normalized-weights) Jp1 K (g/float)))))

(defn- angle-sparse-neuron
  "Create a new neuron with `m` weights, including the bias as last
  weight, within which there are nb-nonzeros non-zero weights, with
  nb-nonzeros < m since the last coordinate of m is for the bias"
  [m]
  (let [nb-nonzeros (g/uniform 1 (min 6 m))
        normalized-weights (normalized-weights nb-nonzeros)
        bias (bias normalized-weights)]
    (-> (vec (repeat (- m nb-nonzeros 1) 0.0))
        (into normalized-weights)
        g/shuffle
        (conj bias))))

(defn angle-sparse-weights
  "Explained in arbre.md"
  [m n seed]
  (binding [g/*rnd* (java.util.Random. seed)]
    (transpose (vec (repeatedly n #(angle-sparse-neuron m))))))

(s/def angle-sparse-weights mzn/weights-generating-fn-spec)
