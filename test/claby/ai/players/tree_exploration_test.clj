(ns claby.ai.players.tree-exploration-test
  (:require [claby.ai.players.tree-exploration :as sut]
            [clojure.test :refer [deftest is]]
            [claby.ai.world :as aiw]
            [claby.game.state-test :as gst]
            [claby.game.events :as ge]
            [claby.game.state :as gs]
            [claby.ai.player :as aip]
            [clojure.spec.test.alpha :as st]))

(st/instrument)
(deftest uct-player-test
  (let [world-state (-> gst/test-state-2
                        (assoc ::gs/player-position [2 0])
                        aiw/get-initial-world-state)
        tree-root (-> (sut/tree-exploration-player {::sut/frequency 0
                                                    ::sut/children {}}
                                                   100)
                      (aip/update-player world-state)
                      :root-node)]
    (is (= (::sut/frequency tree-root) 100))
    (is (= ge/directions (set (keys (::sut/children tree-root)))))
    (is (every? #(= (::sut/frequency %) 25) (vals (::sut/children tree-root))))
    (is (= (-> tree-root ::sut/children :up ::sut/children :right ::sut/value) 0))
    (is (= (-> tree-root ::sut/children :up ::sut/value) 1))
    (is (= (-> tree-root ::sut/value) 2))))
