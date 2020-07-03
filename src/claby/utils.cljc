(ns claby.utils
  "A few utilities."
  (:require
   #?@(:cljs [[cljs.test :refer-macros [deftest is testing]]
              [clojure.test.check]
              [clojure.test.check.properties]
              [cljs.spec.test.alpha :as st]]
       :clj [[clojure.spec.test.alpha :as st]
             [clojure.test :refer [is deftest testing]]])))

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
  [sym]
  (-> (st/check #?(:cljs 'sym :clj sym))
      first
      st/abbrev-result
      (select-keys [:failure :sym])
      :failure))

(defmacro check-all-specs
  "Tests all specs in namespace ns using test.check"
  [symbs]
  (let [ns-specs (st/enumerate-namespace #?(:cljs 'symbs :clj symbs))]
    (-> (map (fn [to-check]
               `(testing ~(str "Testing spec " to-check)
                  (is (not (check-failure (quote ~to-check))))))
             ns-specs)
        (conj 'check-namespace-specs)
        (conj 'deftest))))

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

(defmacro time
  "Returns the time in miliseconds to run the expression, as a
  float--that is, taking into account micro/nanoseconds, subject to
  the underlying platform's precision."
  [expr]
  `(let [start-time# (System/nanoTime)] ~expr (/ (- (System/nanoTime) start-time#) 1000000)))
