(ns claby.ai.player-test
  (:require [claby.ai.player :as sut]
            [clojure.test :refer [deftest is]]
            [clojure.spec.test.alpha :as st]
            [claby.ai.world :as aiw]
            [claby.game.state :as gs]
            [claby.utils.testing :refer [check-spec]]
            [claby.game.state-test :as gst]))

(st/instrument)

(check-spec `sut/get-player-senses
            {:clojure.spec.test.check/opts {:num-tests 100}})

(deftest get-board-subset-test
  
  (let [world-state (aiw/get-initial-world-state gst/test-state-2)
        world-state-2 (aiw/get-initial-world-state
                       (assoc gst/test-state-2 ::gs/player-position [0 4]))]
    (is (= (::sut/board-subset (sut/get-player-senses world-state 1))
         [[:empty :wall :empty]
          [:fruit :empty :empty]
          [:empty :wall :empty]]))
    (is (= (::sut/board-subset (sut/get-player-senses world-state-2 1))
           [[:empty :empty :empty]
            [:empty :empty :empty]
            [:empty :empty :empty]]))))
