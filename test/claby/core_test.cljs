(ns claby.core-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [clojure.test.check]
   [clojure.test.check.properties]
   [cljs.spec.test.alpha
    :refer-macros [instrument check]
    :refer [abbrev-result]]
   #_[claby.utils :refer-macros [instrument-and-check-all]]
   [claby.core :as c]))

;; (instrument)

(deftest test-all-specs
  (is (= () (->> (check)
                 (map #(select-keys (abbrev-result %) [:failure :sym]))
                 (remove #(not (:sym %)))
                 ;; if spec conformance test failed, returns failure data 
                 (filter #(:failure %))))))
