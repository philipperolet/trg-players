(ns mzero.ai.ann.common-test
  (:require [clojure.test :refer [is]]
            [mzero.ai.ann.common :as sut]
            [mzero.utils.testing :refer [deftest]]
            [uncomplicate.neanderthal.native :as nn]))

(deftest vect=-test
  (is (sut/vect= (nn/fv 3 2.1 -1.0) (nn/fv 3 2.1 -1.0)))
  (is (not (sut/vect= (nn/fv 3 2.1 -1.0) (nn/fv 3.1 2.1 -1.0)))))

(deftest matrix=-test
  (is (sut/matrix= (nn/fge 3 2 [[1 2 3] [-1.1 2.2 3.3]])
                   (nn/fge 3 2 [[1 2 3] [-1.1 2.2 3.3]])))
  (is (not (sut/matrix=
            (nn/fge 3 2 [[1 2 3] [-1.1 2.2 3.3]])
            (nn/fge 3 2 [[1 2 3] [-1.11 2.2 3.3]])))))

(deftest values-in-test
  (is (sut/values-in? (nn/fge 5 3) 0 1))
  (is (sut/values-in? (nn/fv 9) 0 1))
  (is (not (sut/values-in? (nn/fge 3 2) 1 2)))
  (is (not (sut/values-in? (nn/fv 3) 1 2)))
  (is (sut/values-in? (nn/fge 3 2 [-0.99999999 1.5 3 2.7 11.9999999 0]) -1 12))
  (is (sut/values-in? (nn/fv [7 3.4 -0.4 200000000]) -1 Float/MAX_VALUE))
  (is (not (sut/values-in? (nn/fv [-1 2 Float/POSITIVE_INFINITY])
                           -3
                           Float/MAX_VALUE))))
(deftest tens-vec-test
  (let [v1 [1.0 2.0 3.0]
        v2 [[1.0 2.0 3.0] [3.0 4.0 5.0]]]
    (is (= (sut/tens->vec (nn/fv v1)) v1))
    (is (= (sut/tens->vec (nn/fge v2)) v2))))

(deftest rng-serialization
  (let [test-rng (java.util.Random. 42)
        test-rng2 (java.util.Random. 43)
        test-rng3 (java.util.Random. 42)
        reloaded-test-rng (sut/deserialize-rng (sut/serialize-rng test-rng))]
    (is (= (.nextDouble test-rng)
           (.nextDouble (sut/deserialize-rng (sut/serialize-rng test-rng3)))))
    (is (= (.nextDouble test-rng3)
           (.nextDouble reloaded-test-rng)))
    (is (not= test-rng (sut/deserialize-rng (sut/serialize-rng test-rng2))))
    (.nextInt test-rng)
    (.nextInt test-rng)
    (let [decoded-rng (sut/deserialize-rng (sut/serialize-rng test-rng))]
      (.nextInt test-rng3)
      (.nextInt test-rng3)
      (is (= (.nextInt decoded-rng) (.nextInt test-rng3))))
    (let [encoded-rng (sut/serialize-rng test-rng3)]
      (.nextInt test-rng3)
      (is (not= (.nextInt test-rng3) (.nextInt (sut/deserialize-rng encoded-rng)))))))
