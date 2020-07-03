(ns claby.utils-test
  (:require
   #?@(:cljs [[cljs.test :refer-macros [deftest is testing]]
              [clojure.test.check]
              [clojure.test.check.properties]
              [cljs.spec.test.alpha :as st]]
       :clj [[clojure.spec.test.alpha :as st]
             [clojure.test :refer [is deftest testing]]])
   [claby.utils :as u]))

(deftest reduce-until-test
  (is (= (u/reduce-until #(< 8 %) + (range 10))
         10))

  (is (= (u/reduce-until #(< 100 %) + (range 10))
         45))

  (is (= (u/reduce-until #(< 8 %) + 11 (range 10))
         11))

  (is (= (u/reduce-until #(< 100 %) + 90 (range 10))
         105))

  (is (= (u/reduce-until #(< 100 %) + 11 (range 10))
         56)))

(deftest count-calls-test
  (let [test-fn #(* % 3)
        counted-fn (u/count-calls test-fn)]
    (is (= 0 ((:call-count (meta counted-fn)))))
    (is (= 9 (counted-fn (counted-fn 1))))
    (is (= 2 ((:call-count (meta counted-fn)))))
    (is (= 54 (counted-fn (counted-fn (counted-fn 2)))))
    (is (= 3 ((:call-count (meta counted-fn)))))))

(deftest almost=-test
  (is (u/almost= 10 12 2))
  (is (not (u/almost= 10 13 2)))
  (is (u/almost= 1000 1000 3))
  (is (u/almost= 0.1 0.2 1))
  (is (u/almost= 0.1 0.09 0.02))
  (is (not (u/almost= 0.1 0.09 0.005))))

(deftest time-test
  (is (u/almost= (u/time (Thread/sleep 10)) 10 0.2))
  (is (not (u/almost= (u/time (Thread/sleep 10)) 10 0.00001)))
  (is (u/almost= (u/time (Thread/sleep 5))
                 (u/time (Thread/sleep 5))
                 0.1))
  (is (not (u/almost= (u/time (Thread/sleep 5))
                      (u/time (Thread/sleep 5))
                      0.0001))))
