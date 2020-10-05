(ns claby.ai.game-runner-test
  (:require [claby.ai.world :as aiw]
            [claby.ai.player :as aip]
            [clojure.test :refer [is testing deftest are]]
            [claby.utils :as u]
            [claby.game.generation :as gg]
            [claby.ai.game-runner :as gr]
            [claby.ai.main-test :refer [parse-run-args basic-run]]
            [claby.ai.world-test :refer [world-state]]))

(deftest run-test-basic-monothreadrunner
  (basic-run "MonoThreadRunner"))

(deftest run-test-basic-clockedthreadsrunner
  (basic-run "ClockedThreadsRunner"))

(deftest run-test-basic-watcherrunner
  (basic-run "WatcherRunner"))

(deftest update-timing-data-test
  (testing "It should update step timestamp and remaining time, and
  depending on time spent add a misstep"
    (let [game-step-duration 20]
      (are [timestamp time-to-wait missteps]
          (= (gr/update-timing-data world-state timestamp game-step-duration)
             (-> world-state
                 (assoc ::aiw/missteps missteps)
                 (assoc ::aiw/time-to-wait time-to-wait)))
        10 10 0
        5 15 0
        20 0 1
        22 0 1
        30 0 1))))

(deftest run-test-timing-steps
  (let [world-state (atom nil)
        opts (parse-run-args "-p 50 -g 50 -gr ClockedThreadsRunner")]
    (aiw/initialize-game world-state
                         (gg/create-nice-game 8 {::gg/density-map {:fruit 5}})
                         opts)
    (testing "When running a step takes less time to run than game
    step duration, it waits for the remaining time (at ~3ms resolution)"
      (let [player-state (atom (aip/->RandomPlayer))
            start-time (System/currentTimeMillis)]
        (aip/request-movement player-state world-state)
        (gr/run-timed-step world-state opts)
        (is (u/almost= (opts :game-step-duration)
                       (- (System/currentTimeMillis) start-time)
                       3))
        
        (aip/request-movement player-state world-state)
        (gr/run-timed-step world-state opts)
        (is (u/almost= (* 2 (opts :game-step-duration))
                       (- (System/currentTimeMillis) start-time)        
                       6))
        
        (dotimes [_ 5]
          ;; since player & game are not in
          ;; separate threads, player should not
          ;; wait before updating its requests
          ;; for move
          (aip/request-movement player-state world-state)
          (gr/run-timed-step world-state opts))
        (is (u/almost= (* (opts :game-step-duration) 7)
                       (- (System/currentTimeMillis) start-time)
                       21)))
      (testing "When running a step takes more time to run than game
  step duration, it throws"))))
