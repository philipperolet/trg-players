(ns mzero.ai.players.activation-test
  (:require [mzero.ai.players.activation :as sut]
            [mzero.ai.players.network :as mzn]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest check-spec]]
            [mzero.ai.players.common :refer [vect= matrix=]]
            [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.random :as rnd]
            [mzero.utils.utils :as u]
            [clojure.tools.logging :as log]
            [clojure.data.generators :as g]
            [uncomplicate.neanderthal.core :as nc]))


(deftest pdm-test
  (let [inputs (nn/fv 0.5 0.3 1.0)
        patterns (nn/fge 3 2 [[0.5 0.3 0.9] [1 1 1]])
        working-matrix (nn/fge 3 2)]
    (is (matrix= (#'sut/pattern-distance-matrix! inputs
                                                 patterns
                                                 working-matrix)
                 (nn/fge 3 2 [[0 0 0.1] [0.5 0.7 0]])))))

(deftest weight-normalization-test
  (let [weights (nn/fge 3 2 [[0.3 0.8 1.0] [0.5 0.2 0.9]])
        working-matrix (nn/fge 3 2 [[0 0 0.1] [0.25 0.14 0]])]
    (is (matrix=
         (#'sut/weight-normalization! weights working-matrix)
         (nn/fge 3 2 [[0 0 0.047619] [0.15625 0.0875 0]])))))

(deftest unactivated-outputs-test
  (let [working-matrix (nn/fge 3 2 [[0 0 0.1] [0.5 0.7 0]])
        weights (nn/fge 3 2 [[0.3 0.8 1.0] [0.5 0.2 0.9]])
        outputs (nn/fv 2)]
    (is (vect= (#'sut/unactivated-outputs! working-matrix weights outputs)
               (nn/fv 0.1 0.39)))))
(deftest iomr-test
  (is (vect= (#'sut/iomr-activation! (nn/fv 0.0 1.0 0.5 0.2 0.01))
             (nn/fv 1.0 0.0 0.0 0.2 0.96))))

(deftest forward-pass-test
  (let [layer1
        {::mzn/inputs (nn/fv 3) ;; [.5 .3 1.0] then [.3 .8 .5]
         ::mzn/patterns (nn/fge 3 2 [[0.5 0.3 0.9] [1 1 1]])
         ;; [[0 0 .1] [.5 .7 0]] then [[0.2 0.5 0.5] [0.7 0.2 0.5]]
         ::mzn/weights (nn/fge 3 2 [[0.3 0.8 1.0] [0.5 0.2 0.9]])
         ;; [[0 0 0.1] [0.25 0.14 0]] then [[0.06 0.4 0.5] [0.35 0.04 0.45]]
         ::mzn/working-matrix (nn/fge 3 2)
         ;; unactivated output [0.1 0.39] then [0.96 0.84]
         ::mzn/outputs (nn/fv 2)} ;; iomr [0.6 0] then [0 0]
        layer2
        {::mzn/inputs (::mzn/outputs layer1)
         ::mzn/patterns (nn/fge 2 2 [[0.5 0] [0 1]]) 
         ;; [[0.1 0] [0 1]] then [[0.5 0] [0 1]]
         ::mzn/weights (nn/fge 2 2 [[0.7 0.7] [2.0 0.1]])
         ;; [[0.07 0] [0 0.1]] then [[0.35 0] [0 0.1]]
         ::mzn/working-matrix (nn/fge 2 2)
         ;; [0.07 0.01] then [0.35 0.1]
         ::mzn/outputs (nn/fv 2)} ;; iomr [0.72 0.0] then [0 0.6]
        layers [layer1 layer2]]
    (sut/forward-pass! layers '(0.5 0.3 1.0))
    (is (vect= (::mzn/outputs layer1) (nn/fv [0.6 0.0])))
    ;; simultaneous propagation : layer2 has the output of t-1, layer1 of t
    ;; so this is not [0.72 0.0] now but the result of a [0 0] input to layer 2
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.0 0.6])))
    (sut/forward-pass! layers '(0.3 0.8 0.5))
    (is (vect= (::mzn/outputs layer1) (nn/fv [0.0 0.0])))
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.72 0.0]))) ;; but now it is
    (sut/forward-pass! layers '(0.323 0.18 0.55))
    ;; and now it should yield the 2nd result
    (is (vect= (::mzn/outputs layer2) (nn/fv [0.0 0.6])))))

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
        norm-time
        (u/timed (vec (repeatedly 1000 #(#'sut/weight-normalization! w wm))))
        outputs (nn/fv dim)
        unactivated-time
        (u/timed (vec (repeatedly 1000 #(#'sut/unactivated-outputs! wm w outputs))))
        iomr-time
        (u/timed (vec (repeatedly 1000 #(#'sut/iomr-activation! i))))
        dot-prod-time
        (u/timed (vec (repeatedly (* dim 1000) #(nc/dot i i))))]
    (testing "Each matrix op on 1024*1024 matrices should take less than a ms (33% added to avoid brittle tests)"
      (is (< (-> wpdm-time first (/ 1000)) 1.33))
      (println "---\n"(first wpdm-time))
      (is (< (-> norm-time first (/ 1000)) 1.33)))
    (testing "Vector ops should be less than 0.1ms"
      (is (< (-> unactivated-time first (/ 1000)) 0.133))
      (println (first unactivated-time))
      (println "Dot prod " (first dot-prod-time))
      (is (< (-> iomr-time first (/ 1000)) 0.133)))
    (testing "A complete pass on 4 layers should take about 10 ms (
    sum of ops time * 4), so less than 20ms coz we're nice at this time"
      (let [layers
            (mzn/new-layers (rnd/rng-state nn/native-float 42) (repeat 5 dim))
            inputs
            (binding [g/*rnd* (java.util.Random. 42)]
              (vec (repeatedly dim #(g/float))))
            forward-pass
            (u/timed (vec (repeatedly 300 #(sut/forward-pass! layers inputs))))
            single-pass-time
            (/ (first forward-pass) 300)]
        (log/info single-pass-time)
        (is (< single-pass-time 20))))))
