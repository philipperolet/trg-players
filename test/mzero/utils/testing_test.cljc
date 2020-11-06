(ns mzero.utils.testing-test
  #?@
   (:clj
    [(:require [mzero.utils.testing :as u] [clojure.test :refer [deftest is]])]
    :cljs
    [(:require
      [mzero.utils.testing :as u]
      [cljs.test :refer-macros [deftest is]])]))

(deftest count-calls-test
  (let [test-fn #(* % 3)
        counted-fn (u/count-calls test-fn)]
    (is (= 0 ((:call-count (meta counted-fn)))))
    (is (= 9 (counted-fn (counted-fn 1))))
    (is (= 2 ((:call-count (meta counted-fn)))))
    (is (= 54 (counted-fn (counted-fn (counted-fn 2)))))
    (is (= 3 ((:call-count (meta counted-fn)))))))
