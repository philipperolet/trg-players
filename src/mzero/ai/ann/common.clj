(ns mzero.ai.ann.common
  "Common helper functions mostly for working with neanderthal."
  (:require [clojure.spec.alpha :as s]
            [mzero.utils.utils :as u]
            [uncomplicate.neanderthal.core :as nc]))

(s/def ::probability-value (s/and float? #(<= 0.0 % 1.0)))
(s/def ::probability-distribution (s/and (s/every ::probability-value)
                                         (fn [pdistr]
                                           (u/almost= 1.0 (apply + pdistr)))))

(defn tens->vec
  "Convert a neanderthal vector or matrix into a clojure vector.

  Note: since matrix creation in neanderthal takes a list of columns,
  not a list of rows, the conversion here is done accordingly, to
  maintain consistency when converting back & forth."
  [ndt-tensor]
  (if (nc/vctr? ndt-tensor)
    (vec (seq (nc/transfer ndt-tensor)))
    (vec (map (comp tens->vec) (nc/cols ndt-tensor)))))

(defn vect=
  [v1 v2]
  (every? true? (map u/almost= v1 v2)))

(defn matrix=
  [m1 m2]
  (every? true? (map vect= (nc/rows m1) (nc/rows m2))))

(defn per-element-spec
  [spec]
  (fn [vect-or-matr]
    (cond
      (nc/vctr? vect-or-matr)
      (every? #(s/valid? spec %) vect-or-matr)

      (nc/matrix? vect-or-matr)
      (every? (fn [row] (every? #(s/valid? spec %) row)) vect-or-matr))))

(defn values-in?
  [coll minv maxv]
  (s/valid? (per-element-spec (s/and number? #(<= minv % maxv))) coll))

(defn transpose [m]
  (let [get-nth-col (fn [col-idx] (vec (map #(nth % col-idx) m)))]
    (vec (map get-nth-col (range (count (first m)))))))

(s/fdef scale-float
  :args (s/cat :flt (-> (s/double-in 0 1)
                        (s/and float?))
               :low float?
               :hi float?))

(defn scale-float [flt low hi] (+ low (* flt (- hi low))))

(defmulti almost=
  "Like u/almost=, but also accepts a string for `precision`, in which
  case the string must read to a real number between 0 and 1
  exclusive, interpreted as a relative precision : the absolute
  precision will be the first number's value times the relative
  precision"
  (fn [_ _ precision]
    (cond (number? precision) :regular
          (string? precision) :relative)))

(defmethod almost= :regular [a b precision] (u/almost= a b precision))

(defmethod almost= :relative [a b precision]
  (u/almost= a b (* (Math/abs a) (read-string precision))))

(defn coll-almost=
  "Like u/coll-almost= but for colls of colls"
  [c1 c2]
  (if (number? (first c1))
    (u/coll-almost= c1 c2)
    (and (= (count c1) (count c2))
         (every? #(apply coll-almost= %) (map vector c1 c2)))))

(defn serialize-rng
  "Turn a java Random instance to a byte array"
  [rng]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (.writeObject (java.io.ObjectOutputStream. baos) rng)
    (.toByteArray baos)))

(defn deserialize-rng
  [rng-byte-array]
  (let [bais (java.io.ByteArrayInputStream. rng-byte-array)]
    (.readObject (java.io.ObjectInputStream. bais))))
