(ns mzero.ai.players.activation-test
  (:require [mzero.ai.players.activation :as sut]
            [mzero.ai.players.network :as mzn]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.ai.players.common :refer [vect= matrix=]]
            [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.random :as rnd]
            [mzero.utils.utils :as u]
            [clojure.data.generators :as g]
            [uncomplicate.neanderthal.core :as nc]))

(deftest af-test
  (let [test-vec (nn/fv 0.0 1.0 3.5 0.2 0.19 0.01 0.9 0.8 0.79 -0.5 -9.0)]
    (#'sut/af! test-vec)
    (is (vect= test-vec
               (nn/fv 0.0 1.0 1.0 0.2 0.0 0.0 0.9 0.8 0.79 0.0 0.0)))))




(deftest sequential-fp-speed-test
  :unstrumented
  (testing "A complete pass on 4 layers should take about 1 ms ( sum
    of ops time * 4), so less than 2ms (niceness). Strangely seems
    twice as fast as simultaneous fp"
    (let [dim 1024
          layers
          (mzn/new-layers (repeat 5 dim)
                          #(rnd/rand-uniform! (rnd/rng-state nn/native-float 42) (nn/fge %1 %2)))
          inputs
          (binding [g/*rnd* (java.util.Random. 42)]
            (vec (repeatedly dim #(g/float))))
          forward-pass
          (u/timed (vec (repeatedly 100 #(sut/sequential-forward-pass! layers inputs))))
          single-pass-time
          (/ (first forward-pass) 100)]
      (is (< single-pass-time 2)))))

(deftest sequential-forward-pass-test
  (let [layer1
        {::mzn/inputs (nn/fv 3) ;; [.5 .425 1.0] then [.1 .225 0.0]
         ::mzn/weights (nn/fge 3 2 [[-0.3 0.8 1.0] [0.5 2.0 -0.2]])
         ;; unactivated [1.19 0.9] [0.15 0.5]
         ::mzn/outputs (nn/fv 2)} ;; af [1.0 0.9] then [0.0 0.5]
        layer2
        {::mzn/inputs (::mzn/outputs layer1)
         ::mzn/weights (nn/fge 2 2 [[0.7 0.7] [2.0 0.1]])
         ;; unactivated [1.33 2.09] then [0.35 0.05]
         ::mzn/outputs (nn/fv 2)} ;; af [1.0 1.0] then [0.35 0.0]
        layer3
        {::mzn/inputs (::mzn/outputs layer2)
         ::mzn/weights (nn/fge 2 2 [[6.0 -5.7] [0.3 -0.3]])
         ;; unactivated [0.3 0.0] then [2.1 0.105]
         ::mzn/outputs (nn/fv 2)} ;; af [0.3 0.0] then [1.0 0]
        ;; BUT af not applied on Last layer 
        layers [layer1 layer2 layer3]]
    (sut/sequential-forward-pass! layers '(0.5 0.425 1.0))
    (is (vect= (::mzn/outputs layer1) (nn/fv [1.0 0.9])))
    (is (vect= (::mzn/outputs layer2) (nn/fv [1.0 1.0])))
    (is (vect= (::mzn/outputs layer3) (nn/fv [0.3 0.0])))
    (sut/sequential-forward-pass! layers '(0.1 0.225 0.0))
    (is (vect= (::mzn/outputs layer1) (nn/fv [0.0 0.5])))
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.35 0.0])))
    (is (vect= (::mzn/outputs layer3) (nn/fv [2.1 0.105])))))

(deftest sequential-forward-pass-with-b-test
  (let [layer1
        {::mzn/inputs (nn/fv 3) ;; [.5 .425 1.0] then [.1 .225 1.0]
         ::mzn/weights (nn/fge 3 2 [[3.0 -0.5 0.0] [0.5 2.0 -0.2]])
         ;; unactivated [1.19 0.9] [0.15 0.5]
         ::mzn/outputs (nn/fv 2)} ;; af [1.0 1.0] then [0.0 1.0]
        layer2
        {::mzn/inputs (::mzn/outputs layer1)
         ::mzn/weights (nn/fge 2 2 [[0.7 0.7] [2.0 0.1]])
         ;; unactivated [1.4 2.1] then [0.7 0.1]
         ::mzn/outputs (nn/fv 2)} ;; af [1.0 1.0] then [0.7 1.0]
        layer3
        {::mzn/inputs (::mzn/outputs layer2)
         ::mzn/weights (nn/fge 2 2 [[6.0 -5.7] [0.3 -0.3]])
         ;; unactivated [0.3 0.0] then [-1.5 -0.09]
         ::mzn/outputs (nn/fv 2)} ;; af not applied on last layer
        ;; BUT af not applied on Last layer 
        layers [layer1 layer2 layer3]]
    (sut/sequential-forward-pass! layers '(0.5 0.425 1.0) true)
    (is (vect= (::mzn/outputs layer1) (nn/fv [1.0 1.0])))
    (is (vect= (::mzn/outputs layer2) (nn/fv [1.0 1.0])))
    (is (vect= (::mzn/outputs layer3) (nn/fv [0.3 0.0])))
    (sut/sequential-forward-pass! layers '(0.1 0.225 0.0) true)
    (is (vect= (::mzn/outputs layer1) (nn/fv [0.0 1.0])))
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.7 1.0])))
    (is (vect= (::mzn/outputs layer3) (nn/fv [-1.5 -0.09])))))
