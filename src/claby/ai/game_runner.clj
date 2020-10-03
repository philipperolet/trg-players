(ns claby.ai.game-runner
  "Game runner protocol, responsible for starting and managing the world
  and the player.

  Comes with an implementation with clocked threads,
  ClockedThreadsRunner, in which the world and the player update
  regularly at a predetermined frequency, `player-step-duration` and
  `game-step-duration`."
  (:require [claby.ai.player :as aip]
            [claby.ai.world :as aiw]))

(defprotocol GameRunner
  (run-game [runner]))

(defn update-timing-data
  [world new-timestamp game-step-duration]
  (let [step-execution-time (- new-timestamp (-> world ::aiw/step-timestamp))
        time-to-wait (max (- game-step-duration step-execution-time) 0)]
    (-> world
        (assoc ::aiw/time-to-wait time-to-wait)
        (update ::aiw/missteps #(if (zero? time-to-wait) (inc %) %)))))

(defn run-timed-step
  "Runs a step with synchronized timing management, as follows:
  - first compare time since last step's beginning with game step duration;
  - if bigger, count it as a misstep;
  - wait for the remaining amount of time (0 if time was bigger);
  - run the step as usual ("
  [world-state-atom {:as game-options, :keys [game-step-duration logging-steps]}]
  (swap! world-state-atom update-timing-data
         (System/currentTimeMillis) game-step-duration)
  (Thread/sleep (@world-state-atom ::aiw/time-to-wait))
  (aiw/run-step world-state-atom logging-steps))

(defn- run-world
  [world-state game-options]
  (while (aiw/active? @world-state)
    (run-timed-step world-state game-options))
  @world-state)

(defn- run-player
  [world-state player-state player-step-duration]
  (while (aiw/active? @world-state)
    (Thread/sleep player-step-duration)
    (aip/request-movement player-state world-state)))

(defrecord ClockedThreadsRunner [world-state player-state opts]
  GameRunner
  (run-game [{:keys [world-state player-state opts]}]
    (let [game-result
          (future (run-world world-state opts))
          player-result
          (future (run-player world-state player-state (opts :player-step-duration)))]
      
       ;; checks that player does not stop running during game
       ;; execution, then return result
       (while (not (realized? game-result))
         (when (realized? player-result) @player-result))       
       @game-result)))

(defrecord MonoThreadRunner [world-state player-state opts]
  GameRunner
  (run-game [{:keys [world-state player-state opts]}]
    (while (aiw/active? @world-state)
      (aip/request-movement player-state world-state)
      (aiw/run-step world-state (opts :logging-steps)))
    @world-state))
