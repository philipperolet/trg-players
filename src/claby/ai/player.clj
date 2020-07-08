(ns claby.ai.player
  "Base player thread. A player should watch the game via its player
  senses and make movement requests accordingly (see claby.ai.world)

  The game may take up to step-duration to excute movement
  requests. The player algorithm may take it into account."
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


    
  
  
  
  
