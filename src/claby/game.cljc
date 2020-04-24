(ns claby.game
  "The game is about eating fruits in a maze.

  'maze' is a matrix of elements, either empty terrain,
  wall, or fruit.

  'position' is the index (line, col) of the player on the maze
  
  'score' is the total number of fruit eaten."
  (:require [clojure.spec.alpha :as s]))

(def game-size 30)

;; Game state specs

(s/def ::game-cell #{:empty :wall :fruit})
(s/def ::game-line (s/coll-of ::game-cell :kind vector? :count game-size))
(s/def ::game-board (s/coll-of ::game-line :kind vector? :count game-size))

(s/def ::player-position (s/tuple (s/int-in 0 game-size) (s/int-in 0 game-size)))

(s/def ::game-state (s/keys :req [::game-board ::player-position]))

(defn create-game
  "Creates an empty game"
  []
  (println x "Hello, World!"))

(foo 3)
