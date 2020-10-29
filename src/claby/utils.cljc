(ns claby.utils
  "A few utilities."
  (:require
   #?@(:cljs [[cljs.test :refer-macros [deftest is testing]]
              [clojure.test.check]
              [clojure.test.check.properties]
              [cljs.spec.test.alpha :as st]]
       :clj [[clojure.spec.test.alpha :as st]
             [clojure.test :refer [is deftest testing]]])
   [clojure.string :as str]))

(defn check-results 
  "Returns a nicer version of test.check results for a namespace"
  [nsspecs]
  (->> (st/check #?(:cljs 'nsspecs :clj nsspecs))
       (map #(select-keys (st/abbrev-result %) [:failure :sym]))
       (remove #(not (:sym %)))
       ;; if spec conformance test failed, returns failure data 
       (filter #(:failure %))))

(defn check-failure
  "Returns a more pretty version of results of check on a symbol.
  
   If spec conformance test failed, returns failure data, else nil"
  ([sym opts]
   (-> (st/check #?(:cljs 'sym :clj sym) opts)
       first
       st/abbrev-result
       (select-keys [:failure :sym])
       :failure))
  ([sym] (check-failure sym {})))

(defmacro check-spec
  "Creates a test with deftest for a given symbol, with given options."
  ([sym opts]
   (-> `((testing ~(str "Testing spec " sym)
           (is (not (check-failure ~sym ~opts)))))
       (conj (symbol (str "check-spec-" (last (str/split (str sym) #"/")))))
       (conj 'deftest)))
  ([sym] `(check-spec ~sym {})))

(defmacro check-all-specs
  "Check-spec for all specs in namespace ns"
  [symbs]
  (let [ns-specs (st/enumerate-namespace #?(:cljs 'symbs :clj symbs))]
    (-> (map (fn [to-check]
               `(testing ~(str "Testing spec " to-check)
                  (is (not (check-failure (quote ~to-check))))))
             ns-specs)
        (conj 'check-namespace-specs)
        (conj 'deftest))))
(defmacro fake-it [symb opts] (-> `((check-failure ~(str symb) ~opts))
                                  (conj (symbol (str "chek-" symb)))
                                  (conj 'deftest)))
(defn reduce-until
  "Like clojure.core/reduce, but stops reduction when `pred` is
  true. `pred` takes one argument, the current reduction value, before
  applying another step of reduce. If `pred` does not become true,
  returns the result of reduce"
  ([pred f coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) coll))
  ([pred f val coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) val coll)))

(defn count-calls
  "Used in with-redefs to count function calls during testing.

  Returns a new function that mimics `f` but counts the number of
  calls and stores it as metadata.
  Resets count when call count is checked."
  [f]
  (let [n (atom 0)]
    (with-meta
      (comp (fn [x] (swap! n inc) x) f)
      {:call-count (fn [] (first (reset-vals! n 0)))
       :private false})))

(defn abs [x]
  (if (pos? x) x (- x)))

(defn almost=
  "Compares 2 numbers with a given precision, returns true if the
  numbers' difference is lower or equal than the precision"
  [a b precision]
  (<= (abs (- a b)) precision))

(defmacro timed
  "Returns the time in miliseconds to run the expression, as a
  float--that is, taking into account micro/nanoseconds, subject to
  the underlying platform's precision."
  [expr]
  `(let [start-time# (System/nanoTime)
         result# ~expr]
     [(/ (- (System/nanoTime) start-time#) 1000000.0) result#]))

(defn filter-keys
  "Like select-keys, with a predicate on the keys"
  [pred map_]
  (select-keys map_ (filter pred (keys map_))))


(defn remove-common-beginning
  "Checks if seq1 and seq2 begin with a common subsequence, and returns
  the remainder of seq1--that is, seq1 stripped of the common
  subsequence. Returns a lazy sequence."
  [seq1 seq2]
  (if (or (empty? seq1) (not= (first seq1) (first seq2)))
    seq1
    (recur (rest seq1) (rest seq2))))

"ModSubVector : A view on vector vect that is a subvector starting at
`start`, of length `length`, such that:

- start & length can be bigger than vect count or smaller than 0 (via
  modulo (count vect)). In particular the subvec restarts from zero if
  start + length > (count vect)

- the resulting subvector is of size `length`, its first
element (index 0) is vect[position], its last element is
vect[(position + length) % vect-size]."

(deftype ModSubVector [vect start length]
  clojure.lang.Indexed
  (nth [this n m]
    (if (< -1 n length)
      (nth vect (mod (+ start n) (count vect)))
      m))
  (nth [this n]
    (or (nth this n nil)
        (throw (IndexOutOfBoundsException.))))
  
  clojure.lang.Counted
  (count [this] length)
  clojure.lang.IPersistentVector

  clojure.lang.IPersistentCollection
  (equiv [this obj]
    (cond
      (indexed? obj)
      (and (= (count obj) (count this))
           (loop [cnt (dec (count this))]
             (if (= (nth obj cnt) (nth this cnt))
               (or (zero? cnt)
                   (recur (dec cnt)))
               false)))

      (seqable? obj)
      (= (seq this) (seq obj))

      :else
      false))
  
  clojure.lang.Seqable
  (seq [this]
    (reify clojure.lang.ISeq
      (first [this_s] (vect (mod start (count vect))))
      (next [this_s]
        (if (= 1 length) nil (seq (ModSubVector. vect (inc start) (dec length)))))
      (seq [this_s] this_s)
      (more [this_s] (or (next this_s) '()))
      (equiv [this_s obj]
        (and (seqable? obj)
             (let [obj_s (seq obj)]
               (and (= (first this_s) (first obj_s))
                    (= (next this_s) (next obj_s)))))))))

(defn modsubvec
  "Returns a subvector of `vect` starting at `start`, of length
  `length`, using modulo if start+length > (count vect)"
  [vect start length]
  {:pre [(vector? vect)
         (int? start)
         (nat-int? length)
         (< 0 length (inc (count vect)))]}
  (->ModSubVector vect start length))

(defn scaffold [iface]
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

(defn map-map
  "Return a map with `f` applied to each of `m`'s keys"
  [f m]
  (reduce #(update %1 %2 f) m (keys m)))
