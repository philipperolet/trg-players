(ns claby.ai.player
  "Module responsible for the player interface, along with a basic
  random player.

  A Player record updates its state every
  `player-step-duration` via `update-player`.  It can move by putting
  a non-nil direction for its move in its `:next-movement` field.

  The 'world' will then execute the movement, which will reflect in
  the world state. It is possible that the player updates again
  *before* the world's execution of the movement, e.g. if the world
  has been paused by interactive mode. The player may have to take
  that into account, for instance by waiting until the movement is
  actually executed. When this happens, note that requesting the same
  movement rather than not requesting a movement might result in the
  movement being executed twice."
  (:require [claby.ai.world :as aiw]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [claby.game.events :as ge]))

(defprotocol Player
  (update-player [player world]))

(defrecord RandomPlayer []
  Player
  (update-player [player _]
    (assoc player :next-movement (gen/generate (s/gen ::ge/direction)))))


(defn request-movement
  [player-state world-state]
  (swap! player-state update-player @world-state)
  (when (-> @player-state :next-movement)
    (swap! world-state
           assoc-in [::aiw/requested-movements :player]
           (-> @player-state :next-movement))))

(defn play-until-end
  "Basic player loop. `player-step-duration` is the inverse frequency of
  player move requests."
  [world-state player-state player-step-duration]
  (while (aiw/active? @world-state)
    (Thread/sleep player-step-duration)
    (request-movement player-state world-state)))
