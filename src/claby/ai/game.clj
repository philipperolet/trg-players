(ns claby.ai.game
  "Game thread with main game loop, responsible for starting/ending
  the game, updating game state, moving enemies, providing player
  senses"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.game.state :as gs]
            [claby.game.events :as ge]
            [claby.game.generation :as gg]))

(def game-state (atom {}))

(add-watch game-state
           :display-state-after-change
           (fn [_ _ _ new-state] (println (gs/state->string new-state))))

(defn run-game
  "Main game loop"
  []
  (println "The game begins.")
  (reset! game-state (gg/create-nice-game 10 {::gg/density-map {:fruit 5}}))
  (while true
    (Thread/sleep 2000)
    (swap! game-state #(ge/move-player % (gen/generate (s/gen ::ge/direction))))
    (println "2 more secs")))
