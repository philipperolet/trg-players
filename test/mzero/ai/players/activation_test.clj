(ns mzero.ai.players.activation-test
  (:require [mzero.ai.players.activation :as sut]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest check-spec]]
            [mzero.ai.players.common :refer [vect= matrix=]]
            [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.random :as rnd]
            [mzero.utils.utils :as u]
            [uncomplicate.neanderthal.core :as nc]))


(deftest weighted-pdm-test
  (let [inputs (nn/fv 0.5 0.3 1.0)
        patterns (nn/fge 3 2 [[0.5 0.3 0.9] [1 1 1]])
        weights (nn/fge 3 2 [[0.3 0.8 1.0] [0.5 0.2 0.9]])
        working-matrix (nn/fge 3 2)]
    (is (matrix= (#'sut/weighted-pattern-distance-matrix! inputs
                                                           patterns
                                                           weights
                                                           working-matrix)
                      (nn/fge 3 2 [[0 0 0.1] [0.25 0.14 0]])))))

(deftest weight-normalization-test
  (let [weights (nn/fge 3 2 [[0.3 0.8 1.0] [0.5 0.2 0.9]])
        working-matrix (nn/fge 3 2 [[0 0 0.1] [0.25 0.14 0]])]
    (is (matrix=
         (#'sut/weight-normalization! weights working-matrix)
         (nn/fge 3 2 [[0 0 0.047619] [0.15625 0.0875 0]])))))

(deftest unactivated-outputs-test
  (let [working-matrix (nn/fge 3 2 [[0 0 0.047619] [0.15625 0.0875 0]])
        outputs (nn/fv 2)]
    (is (vect= (#'sut/unactivated-outputs! working-matrix outputs)
               (nn/fv 0.047619 0.24375)))))
(deftest iomr-test
  (is (vect= (#'sut/iomr-activation! (nn/fv 0.0 1.0 0.5 0.2 0.01))
             (nn/fv 1.0 0.0 0.0 0.2 0.96))))

(check-spec `sut/new-layers)

(deftest forward-pass-test
  (let [layer1
        {::sut/inputs (nn/fv 3)
         ::sut/patterns (nn/fge 3 2 [[0.5 0.3 0.9] [1 1 1]])
         ::sut/weights (nn/fge 3 2 [[0.3 0.8 1.0] [0.5 0.2 0.9]])
         ::sut/working-matrix (nn/fge 3 2)
         ::sut/outputs (nn/fv 2)}
        layer2
        {::sut/inputs (::sut/outputs layer1)
         ::sut/patterns (nn/fge 2 2 [[0.5 0] [0 1]])
         ::sut/weights (nn/fge 2 2 [[0.7 0.7] [0.7 0.7]])
         ::sut/working-matrix (nn/fge 2 2)
         ::sut/outputs (nn/fv 2)}
        layers [layer1 layer2]]
    (sut/forward-pass! layers '(0.5 0.3 1.0))
    (is (vect= (::sut/outputs layer1) (nn/fv [0.809524 0.0])))
    (is (vect= (::sut/outputs layer2) (nn/fv [0.0 0.0])))
    (sut/forward-pass! layers '(0.3 0.8 0.5))
    (is (vect= (::sut/outputs layer1) (nn/fv [0.0 0.0])))
    (is (vect= (::sut/outputs layer2) (nn/fv [0.380952 0.0])))))

