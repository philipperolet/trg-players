(ns claby.utils-test
  #?@
   (:clj
    [(:require
      [claby.utils :as u]
      [clojure.test :refer [are deftest is]])]
    :cljs
    [(:require
      [claby.utils :as u]
      [cljs.test :refer-macros [deftest is]])]))

(deftest count-calls-test
  (let [test-fn #(* % 3)
        counted-fn (u/count-calls test-fn)]
    (is (= 0 ((:call-count (meta counted-fn)))))
    (is (= 9 (counted-fn (counted-fn 1))))
    (is (= 2 ((:call-count (meta counted-fn)))))
    (is (= 54 (counted-fn (counted-fn (counted-fn 2)))))
    (is (= 3 ((:call-count (meta counted-fn)))))))

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
