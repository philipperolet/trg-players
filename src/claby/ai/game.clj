(ns claby.ai.game
  "Game thread with main game loop, responsible for starting/ending
  the game, updating game state, moving enemies, providing player
  senses"
  (:require [claby.game.generation :as gg]))

(defn run-game
  "Main game loop"
  []
  (println "The game begins.")
  (println (gg/create-nice-game 10 {}))
  (while true
    (Thread/sleep 2000)
    (println "2 more secs")))
