(ns mzero.ai.debug-test
  (:require [mzero.ai.debug :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest]]))

(deftest neural-branch-test
  (let [inputs [0.5 1.0 -0.5]
        weights [-1.0 0.5 -2.0]]
    (is (= (sut/neural-stem inputs weights)
           {:value 1.0
            :neural-branch
            [{:index 2 :weight -2.0 :input -0.5 :contribution 1.0 :percentage 50.0}
             {:index 0 :weight -1.0 :input 0.5 :contribution -0.5 :percentage 25.0}
             {:index 1 :weight 0.5 :input 1.0 :contribution 0.5 :percentage 25.0}]}))))
