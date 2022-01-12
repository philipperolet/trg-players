(ns mzero.ai.players.m0-dqn-test
  (:require [mzero.ai.players.m0-dqn :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.ai.ann.ann :as mzann]))

(deftest eps-decay
  (is (u/almost= 0.1 (nth (iterate #'sut/epsilon-decay 1.0) 5000000))))

(deftest pick-datapoint-replay-batch
  (let [states [[0 0] [1 1] [2 2] [3 3] [4 4] [5 5] [6 6]]
        actions [:up :down :left :up :right :up nil]
        rewards [0.0 1.0 -0.1 nil nil nil nil]
        current-iv [-1 -1]
        sample-dps
        (map #(hash-map ::mzs/state %1 ::mzs/action %2 ::mzs/reward %3)
             states actions rewards)]  
    (with-redefs [sut/replay-batch-size 3]
      (is (= (#'sut/replay-batch sample-dps current-iv)
             '({:st [0 0 1 1 2 2 3 3] :rt 0.0 :at :up :st1 [-1 -1 0 0 1 1 2 2]}
               {:st [1 1 2 2 3 3 4 4] :rt 1.0 :at :down :st1 [0 0 1 1 2 2 3 3]}
               {:st [2 2 3 3 4 4 5 5] :rt -0.1 :at :left :st1 [1 1 2 2 3 3 4 4]}))))))

(deftest create-target-tensor
  :unstrumented
  (let [fake-ann
        (reify mzann/ANN
          (nb-layers [this] 0)
          (-forward-pass! [this input-tensor] this)
          (-layer-data [this lindex lkey]
            [[0.3 0.2 0.1 0.1 0.1] [0.1 0.2 0.5 0.1 0.1] [-0.3 -0.4 0.0 -0.1 -0.1]])
          (-tens->vec [this tensor] tensor))
        replay-batch
        '({:st [0 0 1 1 2 2 3 3] :rt 0.0 :at :up :st1 [-1 -1 0 0 1 1 2 2]}
          {:st [1 1 2 2 3 3 4 4] :rt 1.0 :at :down :st1 [0 0 1 1 2 2 3 3]}
          {:st [2 2 3 3 4 4 5 5] :rt -0.1 :at :left :st1 [1 1 2 2 3 3 4 4]})]
    (is (= (#'sut/create-target-tensor fake-ann replay-batch)
           [[(+ (* 0.98 0.3) 0.0) nil nil nil nil]
            [nil nil (+ (* 0.98 0.5) 1.0) nil nil]
            [nil nil nil (+ 0.0 -0.1) nil]]))))
