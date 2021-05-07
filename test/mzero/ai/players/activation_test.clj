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

(deftest pdm-test
  (let [inputs (nn/fv 0.5 0.3 1.0)
        patterns (nn/fge 3 2 [[0.5 0.3 0.9] [1 1 1]])
        working-matrix (nn/fge 3 2)]
    (#'sut/pattern-distance-matrix! inputs patterns working-matrix)
    (is (matrix= working-matrix
                 (nn/fge 3 2 [[0 0 0.1] [0.5 0.7 0]])))))

(deftest proximity-matrix-test
  (let [working-matrix (nn/fge 3 2 [[0 0 0.1] [0.5 0.7 0]])]
    (#'sut/proximity-matrix! working-matrix)
    (is (matrix= working-matrix
                 (nn/fge [[1 1 0.6] [0 0 1]])))))

(deftest unactivated-outputs-test
  (let [working-matrix (nn/fge [[1 1 0.6] [0 0 1]])
        weights (nn/fge 3 2 [[0.3 0.8 1.0] [0.5 0.2 0.9]])
        weights2 (nn/fge 3 2 [[0.4 -1.0 0.5] [-1.0 -0.5 -0.5]])
        outputs (nn/fv 2)]
    (#'sut/unactivated-outputs! working-matrix weights outputs)
    (is (vect= outputs (nn/fv 1.7 0.9)))
    (#'sut/unactivated-outputs! working-matrix weights2 outputs)
    (is (vect= outputs (nn/fv -0.3 -0.5)))))

(deftest omr-test
  (let [test-vec (nn/fv 0.0 1.0 3.5 0.2 0.19 0.01 0.9 0.8 0.79 -0.5 -9.0)]
    (#'sut/omr! test-vec)
    (is (vect= test-vec
               (nn/fv 0.0 1.0 1.0 0.2 0.0 0.0 0.9 0.8 0.79 0.0 0.0)))))

(deftest operations-speed-test
  :unstrumented
  ;; Basic speed test of activation.clj operations (not tuned to reach
  ;; peak cpu perf)
  (let [dim 1024
        i (rnd/rand-uniform! (nn/fv dim))
        w (rnd/rand-uniform! (nn/fge dim dim))
        [p wm] (repeatedly 2 #(nn/fge dim dim))
        wpdm-time
        (u/timed
         (->> #(#'sut/pattern-distance-matrix! i p wm)
              (repeatedly 1000)
              vec))
        prox-time
        (u/timed (vec (repeatedly 1000 #(#'sut/proximity-matrix! wm))))
        outputs (nn/fv dim)
        unactivated-time
        (u/timed (vec (repeatedly 1000 #(#'sut/unactivated-outputs! wm w outputs))))
        omr-time
        (u/timed (vec (repeatedly 1000 #(#'sut/omr! i))))]
    (testing "Each matrix op on 1024*1024 matrices should take clearly less than a ms"
      (is (< (-> wpdm-time first (/ 1000)) 1))
      (is (< (-> prox-time first (/ 1000)) 1))
      (is (< (-> unactivated-time first (/ 1000)) 1)))
    (testing "Vector ops should be less than 0.1ms"
      (is (< (-> omr-time first (/ 1000)) 0.133)))))

(deftest simultaneous-fp-speed-test
  :unstrumented
  (testing "A complete pass on 4 layers should take about 10 ms (
    sum of ops time * 4), so less than 25ms coz we're nice at this time"
    (let [dim 1024
          layers
          (mzn/new-layers (repeat 5 dim)
                          nn/fge
                          #(rnd/rand-uniform! (rnd/rng-state nn/native-float 42) (nn/fge %1 %2)))
          inputs
          (binding [g/*rnd* (java.util.Random. 42)]
            (vec (repeatedly dim #(g/float))))
          forward-pass
          (u/timed (vec (repeatedly 100 #(sut/simultaneous-forward-pass! layers inputs))))
          single-pass-time
          (/ (first forward-pass) 100)]
      (is (< single-pass-time 25)))))

(deftest sequential-fp-speed-test
  :unstrumented
  (testing "A complete pass on 4 layers should take about 10 ms ( sum
    of ops time * 4), so less than 15ms (niceness). Strangely seems
    twice as fast as simultaneous fp"
    (let [dim 1024
          layers
          (mzn/new-layers (repeat 5 dim)
                          nn/fge
                          #(rnd/rand-uniform! (rnd/rng-state nn/native-float 42) (nn/fge %1 %2)))
          inputs
          (binding [g/*rnd* (java.util.Random. 42)]
            (vec (repeatedly dim #(g/float))))
          forward-pass
          (u/timed (vec (repeatedly 100 #(sut/sequential-forward-pass! layers inputs))))
          single-pass-time
          (/ (first forward-pass) 100)]
      (is (< single-pass-time 15)))))

(deftest simultaneous-forward-pass-test
  (let [layer1
        {::mzn/inputs (nn/fv 3) ;; [.5 .3 1.0] then [.3 .8 .5]
         ::mzn/patterns (nn/fge 3 2 [[0.5 0.3 0.9] [1 1 1]])
         ;; [[0 0 .1] [.5 .7 0]] then [[0.2 0.5 0.5] [0.7 0.2 0.5]]
         ;; [[1 1 0.6] [0 0 1.0]] then [[0.2 0 0] [0 0.2 0]]
         ::mzn/weights (nn/fge 3 2 [[-0.3 0.8 1.0] [0.5 2.0 0.9]])
         ;; [[0.3 0.8 0.6] [0 0 0.9]] then [[-0.06 0 0] [0.0 0.4 0.0]]
         ::mzn/working-matrix (nn/fge 3 2)
         ;; unactivated output [1.1 0.9] then [0 0.4]
         ::mzn/outputs (nn/fv 2)} ;; omr [1.0 0.9] then [0 0.4]
        layer2
        {::mzn/inputs (::mzn/outputs layer1)
         ::mzn/patterns (nn/fge 2 2 [[0.5 0] [0 1]]) 
         ;; [[0.5 0.9] [1.0 0.1]] then [[0.5 0.4] [0 0.6]]
         ;; [[0 0] [0 0.6]] then [[0 0] [1 0]]
         ::mzn/weights (nn/fge 2 2 [[0.7 0.7] [2.0 0.1]])
         ;; [[0 0] [0 0.06]] then [[0.0 0.0] [2.0 0]]
         ::mzn/working-matrix (nn/fge 2 2)
         ;; [0.0 0.06] then [0.0 2.0]
         ::mzn/outputs (nn/fv 2)} ;; iomr [0.0 0.0] then [0 1.0]
        layer3
        {::mzn/inputs (::mzn/outputs layer2)
         ::mzn/patterns (nn/fge 2 2 [[0.1 0.1] [0.1 0.9]]) 
         ;; [[0.1 0.1] [0.1 0.9]] then [[0.1 0.9] [0.9 0.1]]
         ;; [[0.6 0.6] [0.6 0.0]] then [[0.6 0.0] [0 0.6]]
         ::mzn/weights (nn/fge 2 2 [[1.0 -0.7] [1.0 -1.0]])
         ;; [[0.6 -0.42] [0.6 0.0]] then [[0.6 0.0] [0.0 -0.6]]
         ::mzn/working-matrix (nn/fge 2 2)
         ;; [0.18 0.6] then [0.6 0.0]
         ::mzn/outputs (nn/fv 2)} ;; iomr [0.0 0.6] then [0.6 0.0]
        layers [layer1 layer2 layer3]]
    (sut/simultaneous-forward-pass! layers '(0.5 0.3 1.0))
    (is (vect= (::mzn/outputs layer1) (nn/fv [1.0 0.9])))
    ;; simultaneous propagation : layer2 has the output of t-1, layer1 of t
    ;; so this is not [0.72 0.0] now but the result of a [0 0] input to layer 2
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.7 1.0])))
    (sut/simultaneous-forward-pass! layers '(0.3 0.8 0.5))
    (is (vect= (::mzn/outputs layer1) (nn/fv [0.0 0.4])))
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.0 0.0]))) ;; but now it is
    (sut/simultaneous-forward-pass! layers '(0.323 0.18 0.55))
    ;; and now it should yield the 2nd result
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.0 1.0])))
    (is (vect= (::mzn/outputs layer3) (nn/fv [0.0 0.6])))
    (sut/simultaneous-forward-pass! layers '(0.323 0.18 0.55))
    (is (vect= (::mzn/outputs layer3) (nn/fv [0.6 0.0])))))

(deftest sequential-forward-pass-test
  (let [layer1
        {::mzn/inputs (nn/fv 3) ;; [.5 .3 1.0] then [.3 .8 .5]
         ::mzn/patterns (nn/fge 3 2 [[0.5 0.3 0.9] [1 1 1]])
         ;; [[0 0 .1] [.5 .7 0]] then [[0.2 0.5 0.5] [0.7 0.2 0.5]]
         ;; [[1 1 0.6] [0 0 1.0]] then [[0.2 0 0] [0 0.2 0]]
         ::mzn/weights (nn/fge 3 2 [[-0.3 0.8 1.0] [0.5 2.0 0.9]])
         ;; [[0.3 0.8 0.6] [0 0 0.9]] then [[-0.06 0 0] [0.0 0.4 0.0]]
         ::mzn/working-matrix (nn/fge 3 2)
         ;; unactivated output [1.1 0.9] then [0 0.4]
         ::mzn/outputs (nn/fv 2)} ;; omr [1.0 0.9] then [0 0.4]
        layer2
        {::mzn/inputs (::mzn/outputs layer1)
         ::mzn/patterns (nn/fge 2 2 [[0.5 0] [0 1]]) 
         ;; [[0.5 0.9] [1.0 0.1]] then [[0.5 0.4] [0 0.6]]
         ;; [[0 0] [0 0.6]] then [[0 0] [1 0]]
         ::mzn/weights (nn/fge 2 2 [[0.7 0.7] [2.0 0.1]])
         ;; [[0 0] [0 0.06]] then [[0.0 0.0] [2.0 0]]
         ::mzn/working-matrix (nn/fge 2 2)
         ;; [0.0 0.06] then [0.0 2.0]
         ::mzn/outputs (nn/fv 2)} ;; iomr [0.0 0.0] then [0 1.0]
        layer3
        {::mzn/inputs (::mzn/outputs layer2)
         ::mzn/patterns (nn/fge 2 2 [[0.1 0.1] [0.1 0.9]]) 
         ;; [[0.1 0.1] [0.1 0.9]] then [[0.1 0.9] [0.9 0.1]]
         ;; [[0.6 0.6] [0.6 0.0]] then [[0.6 0.0] [0 0.6]]
         ::mzn/weights (nn/fge 2 2 [[1.0 -0.7] [1.0 -1.0]])
         ;; [[0.6 -0.42] [0.6 0.0]] then [[0.6 0.0] [0.0 -0.6]]
         ::mzn/working-matrix (nn/fge 2 2)
         ;; [0.18 0.6] then [0.6 0.0]
         ::mzn/outputs (nn/fv 2)} ;; iomr [0.0 0.6] then [0.6 0.0]
        layers [layer1 layer2 layer3]]
    (sut/sequential-forward-pass! layers '(0.5 0.3 1.0))
    (is (vect= (::mzn/outputs layer1) (nn/fv [1.0 0.9])))
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.0 0.0])))
    (is (vect= (::mzn/outputs layer3) (nn/fv [0.0 0.6])))
    (sut/sequential-forward-pass! layers '(0.3 0.8 0.5))
    (is (vect= (::mzn/outputs layer1) (nn/fv [0.0 0.4])))
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.0 1.0])))
    (is (vect= (::mzn/outputs layer3) (nn/fv [0.6 0.0])))))
