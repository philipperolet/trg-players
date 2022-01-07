(ns mzero.ai.ann.network-test
  (:require [clojure.test :refer [is]]
            [mzero.ai.ann.common :as mzc]
            [mzero.ai.ann.network :as sut]
            [mzero.utils.testing :refer [check-spec deftest]]))

(check-spec `sut/new-layers {:clojure.spec.test.check/opts {:num-tests 100}})

(def test-layers
  (let [layer1
        {::sut/inputs [0.0 0.0 0.0] ;; [.5 .425 1.0] then [.1 .225 1.0]
         ::sut/weights (mzc/transpose [[3.0 -0.5 0.0] [0.5 2.0 -0.2]])
         ::sut/raw-output-gradients [0.0 0.0]
         ::sut/raw-outputs [0.0 0.0]
         ;; unactivated [1.19 0.9] [0.1875 0.3]
         ::sut/outputs [0.0 0.0]} ;; af [1.0 1.0] then [0.0 1.0]
        layer2
        {::sut/inputs (::sut/outputs layer1)
         ::sut/weights (mzc/transpose [[0.7 0.7] [2.0 0.1]])
         ::sut/raw-output-gradients [0.0 0.0]
         ::sut/raw-outputs [0.0 0.0]
         ;; unactivated [1.4 2.1] then [0.7 0.1]
         ::sut/outputs [0.0 0.0]} ;; af [1.0 1.0] then [0.7 1.0]
        layer3
        {::sut/inputs (::sut/outputs layer2)
         ::sut/weights (mzc/transpose [[6.0 -5.7] [0.3 -0.3]])
         ::sut/raw-output-gradients [0.0 0.0]
         ::sut/raw-outputs [0.0 0.0]
         ;; unactivated [0.3 0.0] then [-1.5 -0.09]
         ::sut/outputs [0.0 0.0]}]
    [layer1 layer2 layer3]))

(deftest clear-weights
  (is (= [[1.0 1.0 0.0]
          [1.0 1.0 0.0]
          [1.0 1.0 0.0]]
         (sut/clear-weights (vec (repeat 3 [1.0 1.0 1.0])) 2))))
