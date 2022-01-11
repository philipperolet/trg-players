(ns mzero.ai.players.m0-dqn-test
  (:require [mzero.ai.players.m0-dqn :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [mzero.ai.players.m0-modules.senses :as mzs]))

(deftest eps-decay
  (is (u/almost= 0.1 (nth (iterate #'sut/epsilon-decay 1.0) 5000000))))

(deftest pick-datapoint-replay-batch
  (let [states [[0 0] [1 1] [2 2] [3 3] [4 4] [5 5] [6 6]]
        actions [:up :down :left :up :right :up nil]
        rewards [0.0 1.0 -0.1 nil nil nil nil]
        sample-dps
        (map #(hash-map ::mzs/state %1 ::mzs/action %2 ::mzs/reward %3)
             states actions rewards)]  
    (is (= (#'sut/pick-datapoint sample-dps 0)
           {:rb-inputs [0 0 1 1 2 2 3 3] :rb-targets nil}))
    (is (= (#'sut/pick-datapoint sample-dps 1)
           {:rb-inputs [1 1 2 2 3 3 4 4] :rb-targets [nil nil 1.0 nil nil]}))
    (is (= (#'sut/pick-datapoint sample-dps 2)
           {:rb-inputs [2 2 3 3 4 4 5 5] :rb-targets [nil nil nil 0.0 nil]}))
    (with-redefs [sut/replay-batch-size 3]
      (is (= (#'sut/replay-batch sample-dps)
             '({:rb-inputs [0 0 1 1 2 2 3 3], :rb-targets nil}	  
	       {:rb-inputs [1 1 2 2 3 3 4 4], :rb-targets [nil nil 1.0 nil nil]}
	       {:rb-inputs [2 2 3 3 4 4 5 5], :rb-targets [nil nil nil 0.0 nil]}))))))
