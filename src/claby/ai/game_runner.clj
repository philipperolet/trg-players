(ns claby.ai.game-runner
  "Game runner protocol, responsible for starting and managing the world
  and the player.

  Comes with 2 implementations of the protocol, a straightforward
  monothreaded one, and another one using watch functions to run the
  game.

    
  Implementations are encouraged to enforce 2 constraints:
  
  - **timeliness** of the game, meaning that executing requested
  movements should not take more than 1ms. The program will not halt
  at the first delay over 1ms, for stability. However, it will throw
  an exception if delays happen too much;

  - for multithreaded impls, **thread-safe consistency** between
  `game-state` and `requested-movements`, meaning if an external
  thread sees that `requested-movements` is empty, it means that game
  state has already been updated. Conversely, if `requested-movements`
  is not empty, `game-state` has *not* been updated with those
  movements."
  (:require [claby.ai.player :as aip]
            [claby.ai.world :as aiw]))

(defprotocol GameRunner
  (run-game [runner]))

(defrecord MonoThreadRunner [world-state player-state opts]
  GameRunner
  (run-game [{:keys [world-state player-state opts]}]
    (while (aiw/active? @world-state)
      (aip/request-movement player-state world-state)
      (aiw/run-step world-state (opts :logging-steps)))
    @world-state))

(defrecord WatcherRunner [world-state player-state opts]
  GameRunner
  (run-game [{:keys [world-state player-state opts]}]
    (add-watch world-state :run-world
               (fn [_ _ _ new-st]
                 (when (aiw/active? new-st)
                   (if (-> new-st ::aiw/requested-movements :player)
                     (aiw/run-step world-state (opts :logging-steps))
                     (aip/request-movement player-state world-state)))))))

