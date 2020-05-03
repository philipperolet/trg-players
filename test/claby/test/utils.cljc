(ns claby.test.utils
  "A few testing utilities"
  (:require [clojure.spec.test.alpha :as st]
            [clojure.test :refer [is deftest testing]]))

(defn check-results 
  "Returns a nicer version of test.check results for a namespace"
  [ns-specs]
  (->> (st/check ns-specs)
       (map #(select-keys (st/abbrev-result %) [:failure :sym]))
       (remove #(not (:sym %)))
       ;; if spec conformance test failed, returns failure data 
       (filter #(:failure %))))

(defn check-failure
  "Returns a more pretty version of results of check on a symbol.
  
   If spec conformance test failed, returns failure data, else nil"
  [sym]
  (-> (st/check sym)
      first
      st/abbrev-result
      (select-keys [:failure :sym])
      :failure))

(defmacro check-all-specs
  "Tests all specs in namespace ns using test.check"
  [ns]
  (let [ns-specs (st/enumerate-namespace ns)]
    (-> (map (fn [to-check]
               `(testing ~(str "Testing spec " to-check)
                  (is (not (check-failure (quote ~to-check))))))
             ns-specs)
        (conj 'check-namespace-specs)
        (conj 'deftest))))
