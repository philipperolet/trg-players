(ns claby.ai.main
  "Main thread for AI game"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.ai.game :as aig]
            [claby.game.state :as gs]
            [claby.ai.player :as aip])
  (:gen-class))

;; Running the game
;;;;;;;;;;;;;

(defn -main [& args]
  (printf "Main thread for AI game%n")
  (future (aig/run-game))
  (shutdown-agents))

(defn start-game
  "Starts a game with `initial-data` matching game-data spec (see game.clj).
  Opts is a map containing `:player-step-duration` and `:game-step-duration`"
  ([initial-data opts]
   (let [game-data (atom initial-data)]
     (swap! game-data assoc-in [::gs/game-state ::gs/status] :active)
     (let [game-result (future (aig/run-game game-data (opts :game-step-duration)))]
       (future (aip/run-player game-data (opts :player-step-duration)))
       (shutdown-agents)
       @game-result)))
   
  ([initial-data]
   (start-game initial-data {:player-step-duration 1000 :game-step-duration 200})))
