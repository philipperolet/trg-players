(ns claby.ai.main
  "Main thread for AI game"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.ai.game :as game])
  (:gen-class))

;; Running the game
;;;;;;;;;;;;;

(defn -main [& args]
  (printf "Main thread for AI game%n")
  (future (game/run-game))
  (shutdown-agents))
