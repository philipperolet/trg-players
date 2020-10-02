(ns claby.ai.main-integration-test
  (:require [claby.ai.world :as aiw]
            [claby.ai.main :as aim]
            [claby.ai.player :as aip]
            [claby.game.board :as gb]
            [claby.game.state :as gs]
            [claby.game.state-test :as gst]
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
          game-result (aim/run {:game-step-duration 15
                                :player-step-duration 30
                                :logging-steps 0
                                :player-type "random"}
                        test-state)] 
      (is (= :won (-> game-result ::gs/game-state ::gs/status)))
      
      (is (< 5 (-> game-result ::aiw/game-step)))
      
      (is (= (reduce #(assoc-in %1 %2 :empty)
                     (test-state ::gb/game-board)
                     '([1 1] [3 2] [3 3]))
             (-> game-result ::gs/game-state ::gb/game-board))))))
  
(deftest run-test-interactive
  (testing "Interactive mode should require r as input to run, and
 act n times if there were N steps."
    (let [counting-function (u/count-calls (constantly 0))]
      (with-redefs [aim/run-interactive-mode counting-function]
        (let [game-result (aim/run {:game-step-duration 4
                                    :player-step-duration 8
                                    :interactive true
                                    :number-of-steps 15
                                    :board-size 8
                                    :logging-steps 0
                                    :player-type "random"})]
          (with-in-str "r\n"
            (is (= ((:call-count (meta counting-function)))
                   (int (/ (game-result ::aiw/game-step) 15))))))))))

(deftest run-test-interactive-quit
  (with-in-str "q\n"
    (let [game-result (aim/run {:game-step-duration 100
                                :player-step-duration 200
                                :interactive true
                                :number-of-steps 15
                                :board-size 8
                                :logging-steps 0
                                :player-type "random"})]
      (is (< (game-result ::aiw/game-step) 2)))))


(deftest game-ends-on-player-error-test
  (let [failing-player
        (reify aip/Player
          (update-player [_ _] (throw (RuntimeException. "I crashed!"))))]
    
    (with-redefs [aim/player-create-fn {:failing (constantly failing-player)}]
      
      (is (thrown-with-msg? java.util.concurrent.ExecutionException
                            #".*I crashed!.*"
                            (aim/run {:game-step-duration 15
                                      :player-step-duration 30
                                      :logging-steps 0
                                      :board-size 8
                                      :player-type "failing"}))))))
