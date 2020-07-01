(ns claby.ai.main-integration-test
  (:require [claby.ai.game :as gga]
            [claby.ai.main :as aim]
            [claby.game.board :as gb]
            [claby.game.state :as gs]
            [claby.game.state-test :as gst]
            [claby.utils :refer [check-all-specs]]
            [clojure.spec.test.alpha :as st]
            [clojure.test :refer [deftest is testing]]
            [claby.utils :as u]))

(st/instrument)

(deftest run-game-test-basic
  (testing "Runs a basic game (removing cheese & enemies), expects the
  game to finish well"
    (let [test-state (-> gst/test-state-2
                         (assoc-in [::gb/game-board 3 2] :fruit)
                         (assoc-in [::gb/game-board 3 3] :fruit)
                         (assoc ::gs/enemy-positions []))
          game-result (aim/run
                        {:game-step-duration 50
                         :player-step-duration 100}
                        test-state)] 
      (is (= :won (-> game-result ::gs/game-state ::gs/status)))
      
      (is (< 5 (-> game-result ::gga/game-step)))
      
      (is (= (reduce #(assoc-in %1 %2 :empty)
                     (test-state ::gb/game-board)
                     '([1 1] [3 2] [3 3]))
             (-> game-result ::gs/game-state ::gb/game-board))))))
  
(deftest run-game-test-interactive
  (testing "Interactive mode should require r as input to run, and
 act n times if there were N steps."
    (let [counting-function (u/count-calls (constantly 0))]
      (with-redefs [aim/run-interactive-mode counting-function]
        (let [game-result (aim/run {:game-step-duration 20
                                    :player-step-duration 40
                                    :interactive true
                                    :number-of-steps 15
                                    :board-size 8})]
          (with-in-str "r\n"
            (is (= ((:call-count (meta counting-function)))
                   (int (/ (game-result ::gga/game-step) 15))))))))))

(deftest run-game-test-quit
  (with-in-str "q\n"
    (let [game-result (aim/run {:game-step-duration 20
                                :player-step-duration 40
                                :interactive true
                                :number-of-steps 15
                                :board-size 8})]
      (is (= (game-result ::gga/game-step) 0)))))
