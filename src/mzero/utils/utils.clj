(ns mzero.utils.utils)

(defn is_square
  "Return `true` iff n is a perfect square

  WARNING: may fail for large numbers due to rounding errors"
  [n]
  (= (Math/pow (Math/round (Math/sqrt n)) 2) (float n)))

(defn reduce-until
  "Like clojure.core/reduce, but stops reduction when `pred` is
  true. `pred` takes one argument, the current reduction value, before
  applying another step of reduce. If `pred` does not become true,
  returns the result of reduce"
  ([pred f coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) coll))
  ([pred f val coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) val coll)))

(defn abs [x]
  (if (pos? x) x (- x)))

(defn almost=
  "Compares 2 numbers with a given `precision`, returns true if the
  numbers' difference is lower or equal than the precision

  Precision defaults to a millionth of the first number's value."
  ([a b precision]
   (<= (abs (- a b)) precision))
  ([a b]
   (almost= a b (* (Math/abs a) 0.000001))))

(defmacro timed
  "Returns a vector with 2 values:
  -  time in miliseconds to run the expression, as a
  float--that is, taking into account micro/nanoseconds, subject to
  the underlying platform's precision;
  - expression return value."
  [expr]
  `(let [start-time# (System/nanoTime)
         result# ~expr]
     [(/ (- (System/nanoTime) start-time#) 1000000.0) result#]))

(defn filter-keys
  "Like select-keys, with a predicate on the keys"
  [pred map_]
  (select-keys map_ (filter pred (keys map_))))


(defn filter-vals
  "Like filter-keys, except on vals"
  [pred map_]
  (select-keys map_ (filter #(pred (map_ %)) (keys map_))))

(defn remove-common-beginning
  "Checks if seq1 and seq2 begin with a common subsequence, and returns
  the remainder of seq1--that is, seq1 stripped of the common
  subsequence. Returns a lazy sequence."
  [seq1 seq2]
  (if (or (empty? seq1) (not= (first seq1) (first seq2)))
    seq1
    (recur (rest seq1) (rest seq2))))

(defn map-map
  "Return a map with `f` applied to each of `m`'s keys"
  [f m]
  (reduce-kv (fn [acc k v] (assoc acc k (f v))) {} m))

(defn scaffold
  "Show all the interfaces implemented by given `iface`"
  [iface]
  (doseq [[iface methods] (->> iface .getMethods
                            (map #(vector (.getName (.getDeclaringClass %))
                                    (symbol (.getName %))
                                    (count (.getParameterTypes %))))
                            (group-by first))]
    (println (str "  " iface))
    (doseq [[_ name argcount] methods]
      (println
        (str "    "
          (list name (into ['this] (take argcount (repeatedly gensym)))))))))
