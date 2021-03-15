(ns mzero.utils.utils-test
  (:require [mzero.utils.utils :as u]
            [clojure.test :refer [are deftest is]]))

(deftest almost=-test
  (is (u/almost= 10 12 2))
  (is (not (u/almost= 10 13 2)))
  (is (u/almost= 1000 1000 3))
  (is (u/almost= 0.1 0.2 1))
  (is (u/almost= 0.1 0.09 0.02))
  (is (not (u/almost= 0.1 0.09 0.005)))
  (is (u/almost= 0.1 0.1000001))
  (is (u/almost= 1000.001 1000))
  (is (not (u/almost= 0.1 0.10002)))
  (is (u/almost= 1.0 1.0))
  (is (u/almost= 0.0 0.0))
  (is (u/almost= -1.1 -1.1))
  (is (not (u/almost= -1.1 1.1)))
  (is (not (u/almost= 0.01 0.0100011)))
  (is (not (u/almost= 100.0 100.02)))
  (is (not (u/almost= 0.00000001 0)))
  (is (not (u/almost= 0 0.00000001))))

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

(deftest timed-test
  (is (u/almost= (first (u/timed (Thread/sleep 10))) 10 0.5))
  (is (not (u/almost= (first (u/timed (Thread/sleep 10))) 10 0.00001)))
  (is (u/almost= (first (u/timed (Thread/sleep 10)))
                 (first (u/timed (Thread/sleep 10)))
                 0.5))
  (is (not (u/almost= (first (u/timed (Thread/sleep 5)))
                      (first (u/timed (Thread/sleep 5)))
                      0.0001)))
  (is (= (second (u/timed (* 3 3))) 9)))

(deftest filter-keys-test
  (is (= (u/filter-keys #(>= % 3) {1 :a 2 :b 3 :c 4 :d}) {3 :c 4 :d}))
  (is (= (u/filter-keys #(= 2 (count %)) {"a" 1 "bc" 2 "cde" 3}) {"bc" 2}))
  (is (= (u/filter-keys #(int? %) {:a 2 :b 3}) {})))

(deftest filter-vals-test
  (is (= (u/filter-vals #{1 2} {:a 1 :b 3 :d 0}) {:a 1}))
  (is (= (u/filter-vals #(< 2 %) {:a 1 :b 3 :d 0 :c 5}) {:b 3 :c 5}))
  (is (= (u/filter-vals some? {:a nil :b nil :d 0}) {:d 0})))

(deftest map-map-test
  (is (= {:a 6 :b 4} (u/map-map #(* % 2) {:a 3 :b 2}))))

(deftest remove-common-beginning
  (are [s1 s2 res]
      (= (u/remove-common-beginning s1 s2) res)
    '(1 2 3 4 5) '(1 2 5 4) '(3 4 5)
    [] [] []
    [] '() []
    '(1 2 3) '(4 5 6) '(1 2 3)
    [2 4 7] '(2 4 7 8) []
    '(2 4 7 8) [2 4 7] '(8)))

(deftest fn-name
  (is (= (u/fn-name #'+) "+")))
