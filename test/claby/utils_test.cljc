(ns claby.utils-test
  (:require
   #?@(:cljs [[cljs.test :refer-macros [deftest is testing]]
              [clojure.test.check]
              [clojure.test.check.properties]
              [cljs.spec.test.alpha :as st]]
       :clj [[clojure.spec.test.alpha :as st]
             [clojure.test :refer [is deftest testing are]]])
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
  (is (u/almost= (u/time (Thread/sleep 10)) 10 0.5))
  (is (not (u/almost= (u/time (Thread/sleep 10)) 10 0.00001)))
  (is (u/almost= (u/time (Thread/sleep 10))
                 (u/time (Thread/sleep 10))
                 0.5))
  (is (not (u/almost= (u/time (Thread/sleep 5))
                      (u/time (Thread/sleep 5))
                      0.0001))))

(deftest filter-keys-test
  (is (= (u/filter-keys #(>= % 3) {1 :a 2 :b 3 :c 4 :d}) {3 :c 4 :d}))
  (is (= (u/filter-keys #(= 2 (count %)) {"a" 1 "bc" 2 "cde" 3}) {"bc" 2}))
  (is (= (u/filter-keys #(int? %) {:a 2 :b 3}) {})))

(deftest remove-common-beginning
  (are [s1 s2 res]
      (= (u/remove-common-beginning s1 s2) res)
    '(1 2 3 4 5) '(1 2 5 4) '(3 4 5)
    [] [] []
    [] '() []
    '(1 2 3) '(4 5 6) '(1 2 3)
    [2 4 7] '(2 4 7 8) []
    '(2 4 7 8) [2 4 7] '(8)))

(deftest modsubvec-test
  (let [msv (u/modsubvec [1 2 3 4 5] 3 3)
        msv2 (u/modsubvec [9 11 14 13 8 16] -2 4)
        msv3 (u/modsubvec [9 11 14 13 8 16] -8 4)]
    (are [index res] (= (nth msv index) res)
      0 4
      1 5
      2 1)
    (is (= msv [4 5 1]))
    (is (not= msv [5 1 2]))
    (is (every? true?  (map #(= %1 %2) (seq msv) [4 5 1])))
    (is (= (count msv) 3))
    (is (= msv2 [8 16 9 11]))
    (is (= msv2 msv3))
    (is (= msv2 (seq [8 16 9 11])))
    (is (= (map inc msv2) [9 17 10 12]))))
