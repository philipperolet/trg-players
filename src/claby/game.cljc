(ns claby.game
  "The game is about eating fruits in a maze.
  This namespace defines board & game state and provides utilities to count
  stuff and move on the board."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;;; Game board
;;;;;;;;;;;;;;

(defonce min-board-size 4)
(defonce max-board-size 100)
(defonce max-test-board-size 20)

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
  "Returns a board generator favoring empty cells (more than half)"
  (-> (gen/one-of [(s/gen ::game-cell) (gen/return :empty)])
      (gen/vector size)
      (gen/vector size)))

(s/def ::game-board
  (-> (s/every ::game-line
               :kind vector?
               :min-count min-board-size
               :max-count max-board-size)

      (s/and (fn [gb] (comment "lines and rows have same size")
               (every? #(= (count %) (count gb)) gb))
             (fn [gb] (comment "At least 1 empty cell")
               ;; counts cells without using  the funcion count-cells
               ;; to avoid cyclic reference since count-cells spec
               ;; relies on ::game-board
               (< 0 (->> gb (reduce into) (filter #(= % :empty)) count))))
      
      (s/with-gen #(game-board-generator (generate-test-board-size)))))

(s/fdef count-cells
  :args (s/cat :board ::game-board :value ::game-cell)
  :ret nat-int?
  :fn (fn [s] (comment "Less than the total number of cells")
        (<= (:ret s) (reduce * (repeat 2 (-> s :args :board count))))))

(defn count-cells
  "Number of cells with given value on given board"
  [board value]
  (->> board (reduce into) (filter #(= % value)) count))

(defn board-stats
  [board]
  (let [fc (count-cells board :fruit)
        ec (count-cells board :empty)]
    {:fruit-nb fc
     :non-wall-nb (+ ec fc)
     :fruit-density (-> fc (* 100) (/ (+ fc ec)) int)}))

;; Game state
;;;;;;;;;;;;;

(s/def ::position
  (s/tuple (s/int-in 0 max-board-size)
           (s/int-in 0 max-board-size)))

(s/def ::player-position ::position)

(s/def ::score nat-int?)

(defn- game-state-generator [size]
  (gen/hash-map ::game-board (game-board-generator size)
                ::player-position (gen/vector (gen/choose 0 size) 2)
                ::score (s/gen ::score)))

(s/def ::game-state
  (-> (s/keys :req [::game-board ::player-position ::score])
             ;; player postion is inside board
      (s/and #(< ((% ::player-position) 0) (count (::game-board %)))
             #(< ((% ::player-position) 1) (count (::game-board %)))
             ;; player is on an empty space (not wall or fruit
             #(= (-> % ::game-board (get-in (::player-position %))) :empty))
      (s/with-gen #(game-state-generator (generate-test-board-size)))))

(s/fdef init-game-state
  :args (s/cat :size ::board-size)
  :ret ::game-state
  :fn #(= (-> % :args :size) (-> % :ret ::game-board count)))

(defn init-game-state
  "Initial game with player in upper left corner, empty board and null score."
  [game-size]
  {::score 0
   ::player-position [0 0]
   ::game-board (vec (repeat game-size (vec (repeat game-size :empty))))})

;;; Movement on board
;;;;;;;

(s/def ::direction #{:up :right :down :left})

(s/fdef move-position
  :args (s/cat :position ::position
               :direction ::direction
               :board-size ::board-size)
  :ret ::position)

(defn move-position
  "Given a board position, returns the new position when moving in
  provided direction, by adding direction to position modulo size of board"
  [position direction board-size]
  (->> (case direction :up [-1 0] :right [0 1] :down [1 0] :left [0 -1])
       (map #(mod (+ %1 %2) board-size) position)
       vec))

(s/fdef move-player
  :args (s/cat :state ::game-state
               :direction ::direction)

  :ret ::game-state)

(defn move-player
  "Moves player according to provided direction on given state: returns
  state with updated player-position and board."
  [{:keys [::game-board ::player-position], :as state} direction]
  (let [new-position (move-position player-position direction (count game-board))]

    ;; do not move if wall, move if empty, move & eat if fruit
    (case (get-in game-board new-position)
      :wall state
      :empty (assoc state ::player-position new-position)
      :fruit (-> state
                 (assoc ::player-position new-position)
                 (update ::score inc)
                 (assoc-in (into [::game-board] new-position) :empty)))))

(defn move-player-path
  [state directions]
  "Moves player repeatedly on the given collection of directions"
  (reduce move-player state directions))
