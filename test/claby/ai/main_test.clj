(ns claby.ai.main-test
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

(defn basic-run
  "Runs a basic game (removing cheese & enemies), expects the
  game to finish well"
  [game-runner]
  (let [test-state (-> gst/test-state-2
                       (assoc-in [::gb/game-board 3 2] :fruit)
                       (assoc-in [::gb/game-board 3 3] :fruit)
                       (assoc ::gs/enemy-positions []))
        game-result (first (aim/run
                             (aim/parse-run-args (str "-gr " game-runner))
                             (aiw/get-initial-world-state test-state)))]      
    (is (= :won (-> game-result ::gs/game-state ::gs/status)))
    
    (is (< 5 (-> game-result ::aiw/game-step)))
    
    (is (= (reduce #(assoc-in %1 %2 :empty)
                   (test-state ::gb/game-board)
                   '([1 1] [3 2] [3 3]))
           (-> game-result ::gs/game-state ::gb/game-board)))))

(deftest run-test-basic
  (basic-run "MonoThreadRunner"))

(deftest run-test-interactive
  (testing "Interactive mode should require r as input to run, and
 act n times if there were N steps."
    (let [counting-function (u/count-calls (constantly 0))]
      (with-redefs [aim/run-interactive-mode counting-function]
        (let [game-result (first (aim/run (aim/parse-run-args "-i -n 15")))]
          (with-in-str "r\n"
            (is (= ((:call-count (meta counting-function)))
                   (int (/ (game-result ::aiw/game-step) 15))))))))))

(deftest run-test-interactive-quit
  (with-in-str "q\n"
    (let [game-result (aim/run (aim/parse-run-args "-g 100 -p 200 -i -n 15 -b 8"))]
      (is (< ((first game-result) ::aiw/game-step) 2)))))


(deftest game-ends-on-player-error-test
  (let [failing-player
        (reify aip/Player
          (init-player [player _ _] player)
          (update-player [_ _] (throw (RuntimeException. "I crashed!"))))]
    
    (with-redefs [aip/load-player (constantly failing-player)]
      (is (thrown-with-msg? java.lang.Exception
                            #".*I crashed!.*"
                            (aim/run (aim/parse-run-args "")))))))
