(ns claby.ai.player
  "Base player thread. A player should watch the game via its player
  senses and make movement requests accordingly (see claby.ai.game)

  The game may take up to step-duration to excute movement
  requests. The player algorithm may take it into account."
  (:require [claby.ai.game :as gga]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [claby.game.events :as ge]
            [claby.game.state :as gs]))

(defn request-movement []
  (gen/generate (s/gen ::ge/direction)))

(defn run-player
  "Basic player loop. `player-step-duration` is the inverse frequency of
  player move requests."
  [game-data-atom player-step-duration]
  (while (gga/active? @game-data-atom)
    (Thread/sleep player-step-duration)
    (swap! game-data-atom assoc-in [::gga/requested-movements :player] (request-movement))))


    
  
  
  
  
