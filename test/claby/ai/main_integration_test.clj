(ns claby.ai.main-integration-test
  (:require [claby.ai.game :as aig]
            [claby.ai.main :as aim]
            [claby.game.board :as gb]
            [claby.game.state :as gs]
            [claby.game.state-test :as gst]
            [claby.utils :refer [check-all-specs]]
            [clojure.spec.test.alpha :as st]
            [clojure.test :refer [deftest is testing]]
            [claby.utils :as u]))

(st/instrument)

(deftest run-test-basic
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
      
      (is (< 5 (-> game-result ::aig/game-step)))
      
      (is (= (reduce #(assoc-in %1 %2 :empty)
                     (test-state ::gb/game-board)
                     '([1 1] [3 2] [3 3]))
             (-> game-result ::gs/game-state ::gb/game-board))))))
  
(deftest run-test-interactive
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
                   (int (/ (game-result ::aig/game-step) 15))))))))))

(deftest run-test-interactive-quit
  (with-in-str "q\n"
    (let [game-result (aim/run {:game-step-duration 100
                                :player-step-duration 200
                                :interactive true
                                :number-of-steps 15
                                :board-size 8})]
      (is (< (game-result ::aig/game-step) 2)))))

(deftest run-test-timing-steps
  (testing "When running a step takes less time to run than game
  step duration, it waits for the remaining time (at 1ms resolution)"
    ;; init-game {params}
    ;; run 5 steps and for each check the above
    )
  (testing "When running a step takes more time to run than game step
  duration, it throws"))
