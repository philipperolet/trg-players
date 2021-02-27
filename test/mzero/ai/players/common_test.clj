(ns mzero.ai.players.common-test
  (:require [mzero.ai.players.common :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest]]
            [uncomplicate.neanderthal.native :as nn]))

(deftest vect=-test
  (is (sut/vect= (nn/dv 3 2.1 -1.0) (nn/dv 3 2.1 -1.0)))
  (is (not (sut/vect= (nn/dv 3 2.1 -1.0) (nn/dv 3.1 2.1 -1.0)))))

(deftest matrix=-test
  (is (sut/matrix= (nn/dge 3 2 [[1 2 3] [-1.1 2.2 3.3]])
                   (nn/dge 3 2 [[1 2 3] [-1.1 2.2 3.3]])))
  (is (not (sut/matrix=
            (nn/dge 3 2 [[1 2 3] [-1.1 2.2 3.3]])
            (nn/dge 3 2 [[1 2 3] [-1.11 2.2 3.3]])))))

(deftest values-in-test
  (is (sut/values-in? (nn/dge 5 3) 0 1))
  (is (sut/values-in? (nn/dv 9) 0 1))
  (is (not (sut/values-in? (nn/dge 3 2) 1 2)))
  (is (not (sut/values-in? (nn/dv 3) 1 2)))
  (is (sut/values-in? (nn/dge 3 2 [-0.99999999 1.5 3 2.7 11.9999999 0]) -1 12))
  (is (sut/values-in? (nn/dv [7 3.4 -0.4 200000000]) -1 Float/MAX_VALUE))
  (is (not (sut/values-in? (nn/dv [-1 2 Float/POSITIVE_INFINITY])
                           -3
                           Float/MAX_VALUE))))
