(ns claby.ai.main-test
  (:require [claby.ai.game :as gga]
            [claby.ai.main :as aim]
            [claby.game.board :as gb]
            [claby.game.state :as gs]
            [claby.game.state-test :as gst]
            [claby.utils :refer [check-all-specs]]
            [clojure.spec.test.alpha :as st]
            [clojure.test :refer [deftest is testing]]))

(st/instrument)
(check-all-specs claby.ai.main)

(deftest run-game-test
  (testing "Runs a basic game (removing cheese & enemies), expects the game to finish well"
    (let [test-state (-> gst/test-state-2
                         (assoc-in [::gb/game-board 3 2] :fruit)
                         (assoc-in [::gb/game-board 3 3] :fruit)
                         (assoc ::gs/enemy-positions []))
          initial-data (gga/create-game-with-state test-state)
          game-result (aim/start-game initial-data
                                      {:game-step-duration 50
                                       :player-step-duration 100})]
      (is (#{:won} (-> game-result ::gs/game-state ::gs/status)))
      
      (is (< 5 (-> game-result ::gga/game-step)))
      
      (is (= (reduce #(assoc-in %1 %2 :empty)
                     (test-state ::gb/game-board)
                     '([1 1] [3 2] [3 3]))
             (-> game-result ::gs/game-state ::gb/game-board))))))
