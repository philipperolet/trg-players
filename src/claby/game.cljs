(ns claby.game
  "The game is about eating fruits in a maze.

  'game-board' is a matrix of elements, either empty terrain,
  wall, or fruit.

  'player-position' is the index (line, col) of the player on the maze"  
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;;;
;;; Game state specs
;;;

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
                     #(< ((% ::player-position) 1) (count (::game-board %)))
                     ;; player is not on a wall
                     #(not= (-> % ::game-board (get-in (::player-position %))) :wall))
    #(game-state-generator (generate-test-board-size))))

;;;
;;; Game creation
;;;

(s/fdef create-game
  :args (s/cat :size ::board-size)
  :ret ::game-state
  :fn #(= (-> % :args :size) (-> % :ret ::game-board count)))

(defn create-game
  "Creates an empty game with player in upper left corner and walls & fruits south."
  [game-size]
  {::player-position [0 0]
   ::game-board
   (-> (vec (repeat (- game-size 2) (vec (repeat game-size :empty))))
       (conj (vec (repeat game-size :fruit)))
       (conj (vec (repeat game-size :wall))))})

;;;
;;; Player movement
;;;

(s/fdef move-player
  :args (s/cat :state ::game-state
               :direction #{:up :right :down :left})

  :ret ::game-state)

(defn move-player
  "Moves player according to provided direction on given state: returns
  state with updated player-position and board."
  [{:keys [::game-board ::player-position], :as state} direction]
  (->> (case direction :up [-1 0] :right [0 1] :down [1 0] :left [0 -1])

       ;; add direction to player position modulo size of board
       (map #(mod (+ %1 %2) (count game-board)) player-position)
       vec
       
       ;; do not move if wall
       (#(if (= ((game-board (% 0)) (% 1)) :wall)
          state
          (assoc state ::player-position %)))))

(defn move-player-path
  [state directions]
  "Moves player repeatedly on the given collection of directions"
  (reduce move-player state directions))
