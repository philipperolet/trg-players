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
