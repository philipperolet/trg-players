(ns claby.game
  "The game is about eating fruits in a maze.

  'maze' is a matrix of elements, either empty terrain,
  wall, or fruit.

  'player-position' is the index (line, col) of the player on the maze
  
  'score' is the total number of fruit eaten."
  (:require [clojure.spec.alpha :as s]))

(def game-size 30)

;; Game state specs

(s/def ::game-cell #{:empty :wall :fruit})
(s/def ::game-line (s/coll-of ::game-cell :kind vector? :count game-size))
(s/def ::game-board (s/coll-of ::game-line :kind vector? :count game-size))

(s/def ::player-position (s/tuple (s/int-in 0 game-size) (s/int-in 0 game-size)))

(s/def ::game-state (s/keys :req [::game-board ::player-position]))

(s/fdef create-game
  :ret ::game-state)

(defn create-game
  "Creates an empty game with player in upper left corner and walls & fruits south."
  []
  {::player-position [1 1]
   ::game-board
   (-> (vec (repeat (- game-size 2) (vec (repeat game-size :empty))))
       (conj (vec (repeat game-size :fruit)))
       (conj (vec (repeat game-size :wall))))})
      
;;;
;;; Player movement
;;;

(s/fdef move-player
  :args (s/cat :game-state ::game-state
               :direction #{:up :right :down :left})

  :ret ::game-state)
