(ns claby.ai.player
  "Module responsible for the player interface, along with a basic
  random player. A player makes movement requests regularly, through
  function `request-movement`"
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
  (swap! world-state
         assoc-in [::aiw/requested-movements :player]
         (-> @player-state :next-movement)))

(defn play-until-end
  "Basic player loop. `player-step-duration` is the inverse frequency of
  player move requests."
  [world-state player-state player-step-duration]
  (while (aiw/active? @world-state)
    (Thread/sleep player-step-duration)
    (request-movement player-state world-state)))
