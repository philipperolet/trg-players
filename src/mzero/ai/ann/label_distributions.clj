(ns mzero.ai.ann.label-distributions
  "Label distribution fns: `softmax` and `normalized-relinear`"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [mzero.ai.ann.common :as mzc]
            [uncomplicate.neanderthal.math :as nm]
            [mzero.ai.ann.network :as mzn]))

(defmulti softmax
  "In order to avoid overflow, approximations must be made to compute
  e^x_i/sum_j(e^x_j). Compute result with 0.0001 precision or better

  WARNING: test for this function relied on torch, which was removed
  in v0.0.1.0. The function is not tested anymore. Change with extreme
  care"
  (fn [tensor] (comment "Dispatch given dimensiont")
    (cond
      (coll? (first tensor)) :matrix
      (coll? tensor) :vector)))

(defmethod softmax :vector
  [vect]
  ;; if vector element < max - ln(nb_classes*10000), result is 0
  ;; within at most 0.0001
  ;; for all elts i close to the max, compute 1+sum_j(e^(x_i - x_j))
  (let [max-elt (apply max vect)
        logprecision (nm/log (* (count vect) 10000))
        large-elt? #(<= (- max-elt logprecision) %)
        large-elts (filter large-elt? vect)
        softmax_i
        (fn [i]
          (if (large-elt? i)
            (/ (reduce #(+ %1 (nm/exp (- %2 i))) 0.0 large-elts))
            0.0))]
    (vec (map softmax_i vect))))

(defmethod softmax :matrix
  [matr]
  (vec (map softmax matr)))

(defn ^:deprecated normalized-relinear
  "Distribution de probas des mouvements calculée à partir des valeurs
  des motoneurones en ne considérant que les valeurs positives et en
  les normalisant

  Deprecated, car pas de solution simple pour conserver un gradient,
  voir [ici](doc/cel-normalized-relin-softplus.jpg) et le doc de
  version."
  [vect]
  (throw (RuntimeException. "Normalized-relinear Unusable as-is, see doc."))
  (let [rectified-vect (map (partial max 0.0) vect)
        sum (apply + 0.0 rectified-vect)]
    (if (pos? sum)
      (vec (map #(/ % sum) rectified-vect))
      ;; if no positive elt, return equiprobability
      (vec (repeat (count vect) (/ 1.0 (count vect)))))))

(defn soft+
  "Approx. computation of soft+, accurate at at least 1E-8"
  [x]
  (cond
    (< x -20) (nm/exp x)
    (<= -20 x 20) (nm/log (inc (nm/exp x)))
    (< 20 x) x))

(defn ansp
  "Approximate normalized
  softplus. Voir [ici](doc/cel-normalized-relin-softplus.jpg) et le
  doc de version."
  [vect]
  (assert (< -50 (apply max vect)) "Values too small, risk of numerical computation errors")
  (let [soft+vect (map soft+ vect)
        sum (apply + 0.0 soft+vect)]
    (map #(/ % sum) soft+vect)))

(defn ansp-gsf
  "approximate gradient scaling for ansp, accurate at at least 1E-8"
  [x]
  (cond
    (< x -20) 1.0
    (<= -20 x 20) (/ (nm/sigmoid x) (nm/log (inc (nm/exp x))))
    (< 20 x) (/ x)))

(def gradient-scaling-factor
  "For a given label distribution fn, there is a gradient scaling
  factor, which is used in the loss derivation computation,
  see [here](cel-derivations.jpg)"
  {softmax (constantly 1.0)
   normalized-relinear (fn [m] (if (pos? m) (/ m) 0.0))
   ansp ansp-gsf})

(s/def ::label-distribution-fn
  (-> (s/fspec
       :args (s/cat :outputs ::mzn/raw-outputs)
       :ret ::mzc/probability-distribution
       :fn (s/and
            (fn [{:keys [args ret]}]
              (comment "same size")
              (= (count (:outputs args)) (count ret)))))
      (s/with-gen #(gen/return softmax))))
