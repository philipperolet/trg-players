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
                             (aim/parse-run-args (str "-r " game-runner))
                             (aiw/get-initial-world-state test-state)))]      
    (is (= :won (-> game-result ::gs/game-state ::gs/status)))
    
    (is (< 5 (-> game-result ::aiw/game-step)))
    
    (is (= (reduce #(assoc-in %1 %2 :empty)
                   (test-state ::gb/game-board)
                   '([1 1] [3 2] [3 3]))
           (-> game-result ::gs/game-state ::gb/game-board)))
    (testing "If number of steps is given, stops after said number of steps"
      (is (= 50 (->> (aim/run (aim/parse-run-args (str "-n 50 -r " game-runner)))
                     first
                     ::aiw/game-step))))))

(deftest run-test-basic
  (basic-run "MonoThreadRunner"))

(deftest run-test-interactive
  (testing "Interactive mode : after going through 2 loops then
  quitting, there should be 3*STEPS performed"
    (with-in-str "\n\nq\n"
      (is (= (-> (aim/run (aim/parse-run-args "-i -n 33")) first ::aiw/game-step)
             99)))))

(deftest run-test-interactive-quit
  (with-in-str "q\n"
    (let [game-result (aim/run (aim/parse-run-args "-i -n 15"))]
      (is (= ((first game-result) ::aiw/game-step) 15)))))


