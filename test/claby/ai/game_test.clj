(ns claby.ai.game-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as st]
            [claby.utils :refer [check-all-specs]]
            [claby.game.state :as gs]
            [claby.game.board :as gb]
            [claby.game.state-test :as gst]
            [claby.ai.game :as gga]))

(st/instrument)
(check-all-specs claby.ai.game)

(deftest run-step-test
  (let [;; add a 2nd fruit to test state board to avoid clearing the
        ;; game by only eating 1 fruit
        test-state (assoc-in gst/test-state-2 [::gb/game-board 4 4] :fruit)
        
        game-data {::gga/required-movements {}
                   ::gga/game-step 0
                   ::gs/game-state test-state}]
    (testing "Basic behaviour"
      (is (= test-state (-> game-data gga/run-step ::gs/game-state)))
    
      (is (= (-> game-data
                 (assoc ::gga/required-movements {0 :up :player :left})
                 gga/run-step
                 (assoc ::gga/required-movements {:player :down 0 :up 1 :right})
                 gga/run-step)
             {::gga/required-movements {}
              ::gga/game-step 2
              ::gs/game-state (-> test-state
                                  (assoc-in [::gb/game-board 1 1] :empty)
                                  (assoc ::gs/player-position [2 1])
                                  (update ::gs/score inc)
                                  (assoc ::gs/enemy-positions [[3 0] [1 0]]))})))
    
    (testing "Game lost or won during step should not err even when
        some movements remain"
      (let [game-data (assoc game-data ::gs/game-state gst/test-state-2)]
        (is (= (-> game-data
                   (assoc ::gga/required-movements {0 :up 1 :down :player :left})
                   gga/run-step
                   (get-in [::gs/game-state ::gs/status]))
               :won))
        
        (is (= (-> game-data
                   (assoc ::gga/required-movements {:player :left 0 :up 1 :down})
                   gga/run-step
                   (get-in [::gs/game-state ::gs/status]))
               :won))

        (is (= (-> game-data
                   (assoc ::gga/required-movements {:player :right 1 :left 0 :up})
                   gga/run-step
                   (get-in [::gs/game-state ::gs/status]))
               :over))

        (is (= (-> game-data
                   (assoc ::gga/required-movements {1 :left 0 :up :player :right})
                   gga/run-step
                   (get-in [::gs/game-state ::gs/status]))
               :over))))))

               
               
