(ns claby.ai.clocked-threads-runner-test
  (:require [claby.ai.world :as aiw]
            [claby.ai.player :as aip]
            [clojure.test :refer [is testing deftest are]]
            [claby.utils :as u]
            [claby.game.generation :as gg]
            [claby.ai.main-test :refer [parse-run-args basic-run]]
            [claby.ai.world-test :as aiwt]
            [claby.ai.clocked-threads-runner :as ctr]
            [claby.ai.players.random :refer [->RandomPlayer]]))

(def timed-world-state
  (assoc aiwt/world-state ::ctr/missteps 0))

(deftest run-test-basic-clockedthreadsrunner
  (basic-run "ClockedThreadsRunner"))

(deftest update-timing-data-test
  (testing "It should update step timestamp and remaining time, and
  depending on time spent add a misstep"
    (let [game-step-duration 20]
      (are [timestamp time-to-wait missteps]
          (= (ctr/update-timing-data timed-world-state timestamp game-step-duration)
             (-> timed-world-state
                 (assoc ::ctr/missteps missteps)
                 (assoc ::ctr/time-to-wait time-to-wait)))
        10 10 0
        5 15 0
        20 0 1
        22 0 1
        30 0 1))))

(deftest run-test-timing-steps
  (let [world-state
        (atom (-> (aiw/get-initial-world-state
                   (gg/create-nice-game 8 {::gg/density-map {:fruit 5}}))
                  (assoc ::ctr/missteps 0)))
        opts (parse-run-args "-p 50 -g 50 -gr ClockedThreadsRunner")]
    (testing "When running a step takes less time to run than game
    step duration, it waits for the remaining time (at ~3ms resolution)"
      (let [player-state (atom (->RandomPlayer))
            _ (aip/request-movement player-state world-state)
            start-time (System/currentTimeMillis)]
        (ctr/run-timed-step world-state opts)
        (is (u/almost= (opts :game-step-duration)
                       (- (System/currentTimeMillis) start-time)
                       3))
        
        (aip/request-movement player-state world-state)
        (ctr/run-timed-step world-state opts)
        (is (u/almost= (* 2 (opts :game-step-duration))
                       (- (System/currentTimeMillis) start-time)        
                       6))
        
        (dotimes [_ 5]
          ;; since player & game are not in
          ;; separate threads, player should not
          ;; wait before updating its requests
          ;; for move
          (aip/request-movement player-state world-state)
          (ctr/run-timed-step world-state opts))
        (is (u/almost= (* (opts :game-step-duration) 7)
                       (- (System/currentTimeMillis) start-time)
                       21)))
      (testing "When running a step takes more time to run than game
  step duration, it throws"))))
