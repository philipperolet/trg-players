(ns claby.ai.game-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as st]
            [claby.utils :refer [check-all-specs]]
            [claby.game.state :as gs]
            [claby.game.board :as gb]
            [claby.game.state-test :as gst]
            [claby.ai.game :as aig]))

(st/instrument)
(check-all-specs claby.ai.game)

(def test-state
  "add a 2nd fruit to test state board to avoid clearing the game by
  only eating 1 fruit"
  (assoc-in gst/test-state-2 [::gb/game-board 4 4] :fruit))

(def full-state (aig/get-initial-full-state test-state))

(deftest compute-new-state-test
  (testing "Basic behaviour"
    (is (= test-state (-> full-state aig/compute-new-state ::gs/game-state)))
    
    (is (= (-> full-state
               (assoc ::aig/requested-movements {0 :up :player :left})
               aig/compute-new-state
               (assoc ::aig/requested-movements {:player :down 0 :up 1 :right})
               aig/compute-new-state)
           {::aig/requested-movements {}
            ::aig/game-step 2
            ::gs/game-state (-> test-state
                                (assoc-in [::gb/game-board 1 1] :empty)
                                (assoc ::gs/player-position [2 1])
                                (update ::gs/score inc)
                                (assoc ::gs/enemy-positions [[3 0] [1 0]]))})))
  
  (testing "Game lost or won during step should not err even when
        some movements remain"
    (let [full-state (assoc full-state ::gs/game-state gst/test-state-2)]
      (is (= (-> full-state
                 (assoc ::aig/requested-movements {0 :up 1 :down :player :left})
                 aig/compute-new-state
                 (get-in [::gs/game-state ::gs/status]))
             :won))
      
      (is (= (-> full-state
                 (assoc ::aig/requested-movements {:player :left 0 :up 1 :down})
                 aig/compute-new-state
                 (get-in [::gs/game-state ::gs/status]))
             :won))

      (is (= (-> full-state
                 (assoc ::aig/requested-movements {:player :right 1 :left 0 :up})
                 aig/compute-new-state
                 (get-in [::gs/game-state ::gs/status]))
             :over))

      (is (= (-> full-state
                 (assoc ::aig/requested-movements {1 :left 0 :up :player :right})
                 aig/compute-new-state
                 (get-in [::gs/game-state ::gs/status]))
             :over)))))

