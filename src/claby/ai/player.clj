(ns claby.ai.player
  "Module responsible for the player. A player makes movement requests
  regularly, through `play-move`. The function handling the
  movement requests is `request-movement`"
  (:require [claby.ai.world :as aiw]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [claby.game.events :as ge]
            [claby.game.state :as gs]))

(defn request-movement []
  (gen/generate (s/gen ::ge/direction)))

(defn play-move
  [state-atom player-step-duration]
  (Thread/sleep player-step-duration)
  (swap! state-atom assoc-in [::aiw/requested-movements :player] (request-movement)))

(defn play-until-end
  "Basic player loop. `player-step-duration` is the inverse frequency of
  player move requests."
  [state-atom player-step-duration]
  (while (aiw/active? @state-atom)
    (play-move state-atom player-step-duration)))


    
  
  
  
  
