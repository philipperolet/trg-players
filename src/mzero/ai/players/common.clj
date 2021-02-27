(ns mzero.ai.players.common
  "Common helper functions mostly for working with neanderthal."
  (:require [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.core :as nc]
            [mzero.utils.utils :as u]))

(def max-ones-size 10000)
(def ones-vector (nn/dv (repeat max-ones-size 1.0)))

(defn ones
  "Return a vector of `size` ones (double).
  A subvector of `ones-vector` is used, for performance.
  It should not be altered (use copy if needed)"
  [size]
  {:pre [(<= size max-ones-size)]}
  (nc/subvector ones-vector 0 size))

(defn vect=
  [v1 v2]
  (every? true? (map u/almost= v1 v2)))

(defn matrix=
  [m1 m2]
  (every? true? (map vect= (nc/rows m1) (nc/rows m2))))

(defmulti values-in?
  (fn [coll _ _]
    (cond (nc/matrix? coll) :matrix
          (nc/vctr? coll) :vector)))

(defmethod values-in? :vector
  [vect minv maxv]
  (every? #(<= minv % maxv) vect))

(defmethod values-in? :matrix
  [matr minv maxv]
  (every? (fn [row] (every? #(<= minv % maxv) row)) matr))

