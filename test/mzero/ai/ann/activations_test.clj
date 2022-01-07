(ns mzero.ai.ann.activations-test
  (:require [clojure.test :refer [is]]
            [mzero.ai.ann.activations :as sut]
            [mzero.ai.ann.common :as mzc]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [uncomplicate.neanderthal.core :as nc]
            [uncomplicate.neanderthal.native :as nn]))

(def test-ann-impl {:ones (nn/fge 20 20 (repeat 400 1.0))
                    :zeros (nn/fge 20 20 (repeat 400 0.0))
                    :buffer (nn/fge 20 20 (repeat 400 0.0))})

(deftest usual-test
  (let [test-vec_ (nn/fv 0.0 1.01 3.5 0.2 0.19 0.21 0.01 0.99 0.8 0.79 -0.5 -9.0)
        test-vec (nc/view-ge test-vec_ (nc/dim test-vec_) 1)
        output (nc/zero test-vec)
        test-deriv (#'sut/usual-deriv! test-vec)]
    (sut/usual! {:zeros (nn/fge (nc/dim test-vec) 1)
                 :ones (nc/entry! (nn/fge (nc/dim test-vec) 1) 1)}
                test-vec
                output)
    (is (u/coll-almost= (first (mzc/tens->vec output))
                        (mzc/tens->vec (nn/fv 0.0 1.0 1.0 0.0 0.0 0.21 0.0 0.99 0.8 0.79 0.0 0.0))))
    (is (u/coll-almost= (first (mzc/tens->vec test-deriv))
                        [0.0 0.0 0.0 0.0 0.0 1.0 0.0 1.0 1.0 1.0 0.0 0.0]))))

(deftest trelu-test
  (is (= (sut/trelu! test-ann-impl (nn/fge [[-1.0 0.0 0.1 0.9 1.0 2.2]]) (nn/fge 6 1))
         (nn/fge [[0.0 0.0 0.1 0.9 1.0 1.0]])))
  (is (= (sut/trelu-deriv! test-ann-impl (nn/fge [[-1.0 0.0 0.1 0.9 1.0 2.2]]))
         (nn/fge [[0.0 0.0 1.0 1.0 0.0 0.0]]))))

(deftest spike-test
  (is (u/coll-almost=
       (-> (nn/fge [[-1.0 0.0 0.1 0.29 0.31 0.39 0.41 0.5 0.59 0.61 0.69 0.71 1.0 2.0]])
           (#(sut/spike! test-ann-impl %(nn/fge 14 1)))
           (nc/col 0)
           mzc/tens->vec)
       [0.0 0.0 0.0 0.0 0.1 0.9 1.0 1.0 1.0 0.9 0.1 0.0 0.0 0.0]))
  (is (u/coll-almost=
       (-> (nn/fge [[-1.0 0.0 0.1 0.29 0.31 0.39 0.41 0.5 0.59 0.61 0.69 0.71 1.0 2.0]])
           (#(sut/spike-deriv! test-ann-impl %))
           (nc/col 0)
           mzc/tens->vec)
       [0.0 0.0 0.0 0.0 1.0 1.0 0.0 0.0 0.0 1.0 1.0 0.0 0.0 0.0])))
