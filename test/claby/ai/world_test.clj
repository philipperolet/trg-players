(ns claby.ai.world-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as st]
            [claby.utils :refer [check-all-specs]]
            [claby.game.state :as gs]
            [claby.game.board :as gb]
            [claby.game.state-test :as gst]
            [claby.ai.world :as aiw]))

(st/instrument)
(check-all-specs claby.ai.world)

(def test-state
  "add a 2nd fruit to test state board to avoid clearing the game by
  only eating 1 fruit"
  (assoc-in gst/test-state-2 [::gb/game-board 4 4] :fruit))

(def full-state (aiw/get-initial-full-state test-state 0))

(deftest compute-new-state-test
  (testing "Basic behaviour, correctly updating world state on movement requests."
    (is (= test-state (-> full-state (aiw/compute-new-state) ::gs/game-state)))
    
    (is (= (-> full-state
               (assoc ::aiw/requested-movements {0 :up :player :left})
               (aiw/compute-new-state)
               (assoc ::aiw/requested-movements {:player :down 0 :up 1 :right})
               (aiw/compute-new-state)
               (dissoc ::aiw/missteps ::aiw/step-timestamp ::aiw/time-to-wait))
           {::aiw/requested-movements {}
            ::aiw/game-step 2
            ::gs/game-state (-> test-state
                                (assoc-in [::gb/game-board 1 1] :empty)
                                (assoc ::gs/player-position [2 1])
                                (update ::gs/score inc)
                                (assoc ::gs/enemy-positions [[3 0] [1 0]]))})))
  
  (testing "Game lost or won during step should not err even when
        some movements remain"
    (let [full-state (assoc full-state ::gs/game-state gst/test-state-2)]
      (is (= (-> full-state
                 (assoc ::aiw/requested-movements {0 :up 1 :down :player :left})
                 (aiw/compute-new-state)
                 (get-in [::gs/game-state ::gs/status]))
             :won))
      
      (is (= (-> full-state
                 (assoc ::aiw/requested-movements {:player :left 0 :up 1 :down})
                 (aiw/compute-new-state)
                 (get-in [::gs/game-state ::gs/status]))
             :won))

      (is (= (-> full-state
                 (assoc ::aiw/requested-movements {:player :right 1 :left 0 :up})
                 (aiw/compute-new-state)
                 (get-in [::gs/game-state ::gs/status]))
             :over))

      (is (= (-> full-state
                 (assoc ::aiw/requested-movements {1 :left 0 :up :player :right})
                 (aiw/compute-new-state)
                 (get-in [::gs/game-state ::gs/status]))
             :over)))))

(deftest update-timing-data-test
  (testing "It should update step timestamp and remaining time, and
  depending on time spent add a misstep"
    (let [game-step-duration 20]
      (are [timestamp time-to-wait missteps]
          (= (aiw/update-timing-data full-state timestamp game-step-duration)
             (-> full-state
                 (assoc ::aiw/missteps missteps)
                 (assoc ::aiw/time-to-wait time-to-wait)))
        10 10 0
        5 15 0
        20 0 1
        22 0 1
        30 0 1))))
