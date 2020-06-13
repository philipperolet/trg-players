(ns claby.ai.game
  "Game thread with main game loop, responsible for starting/ending
  the game, updating game state, providing player senses, executing
  player and enemies movement requests.

  The game runs in discretized time, with a specified duration.
  At every @step, the game checks whether movements are requested via the
  @movement-queue, executes them and updates the @game-state and corresponding
  @player-senses.

  The game makes no explicit attempt to synchronize movement requests
  reading and execution, meaning the player may see that a movement
  request has been read, but the player senses may not have been
  updated.

  However, an execution that takes longer than the duration of a step
  will be considered an exception."
  
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.game.state :as gs]
            [claby.game.events :as ge]
            [claby.game.generation :as gg]))

(def game-state (atom {}))

(def game-step (atom 0))

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

(s/fdef run-step
  :args (s/cat :state ::gs/game-state :movements
