(ns claby.game
  "The game is about eating fruits in a maze.

  'maze' is a matrix of elements, either empty terrain,
  wall, or fruit.

  'player-position' is the index (line, col) of the player on the maze
  
  'score' is the total number of fruit eaten."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;; Game state specs
(defonce min-board-size 5)
(defonce max-board-size 100)
(defonce max-test-board-size 10)

(s/def ::board-size (s/int-in min-board-size max-board-size))

(s/def ::game-cell #{:empty :wall :fruit})

(s/def ::game-line (s/every ::game-cell
                            :kind vector?
                            :min-count min-board-size
                            :max-count max-board-size
                            :gen-max max-test-board-size))

(defn- generate-test-board-size []
  (gen/generate (s/gen (s/int-in min-board-size max-test-board-size))))

(defn- game-board-generator [size]
  (gen/vector (gen/vector (s/gen ::game-cell) size) size))

(s/def ::game-board
  (s/with-gen (s/and (s/every ::game-line
                              :kind vector?
                              :min-count min-board-size
                              :max-count max-board-size)
                     ;; lines and rows have same size
                     (fn [board] (every? #(= (count %) (count board)) board)))
    #(game-board-generator (generate-test-board-size))))
      

(s/def ::player-position
  (s/tuple (s/int-in 0 max-board-size)
           (s/int-in 0 max-board-size)))

(defn- game-state-generator [size]
  (gen/hash-map ::game-board (game-board-generator size)
                ::player-position (gen/vector (s/gen (s/int-in 0 size)) 2)))

(s/def ::game-state
  (s/with-gen (s/and (s/keys :req [::game-board ::player-position])
                     ;; player postion is inside board
                     #(< ((% ::player-position) 0) (count (::game-board %)))
                     #(< ((% ::player-position) 1) (count (::game-board %))))
    #(game-state-generator (generate-test-board-size))))
    
(s/fdef create-game
  :args ::board-size
  :ret ::game-state)

(defn create-game
  "Creates an empty game with player in upper left corner and walls & fruits south."
  [game-size]
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
