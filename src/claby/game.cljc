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

(s/fdef count-cells
  :args (s/cat :board ::game-board :value ::game-cell)
  :ret nat-int?
  :fn (fn [s] (comment "Less than the total number of cells")
        (<= (:ret s) (reduce * (repeat 2 (-> s :args :board count))))))

(defn- count-cells
  "Number of cells with given value on given board"
  [board value]
  (->> board (reduce into) (filter #(= % value)) count))

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
      

(s/def ::position
  (s/tuple (s/int-in 0 max-board-size)
           (s/int-in 0 max-board-size)))

(s/def ::player-position ::position)

(s/def ::direction #{:up :right :down :left})

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

;;;
;;; Initial game and board creation
;;;

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

(s/fdef move-position
  :args (s/cat :position ::position
               :direction ::direction
               :board-size ::board-size)
  :ret ::position)

(defn- move-position
  "Given a board position, returns the new position when moving in
  provided direction, by adding direction to position modulo size of board"
  [position direction board-size]
  (->> (case direction :up [-1 0] :right [0 1] :down [1 0] :left [0 -1])
       (map #(mod (+ %1 %2) board-size) position)
       vec))

(defn generate-wall
  "Generates a random wall of given length for a given board size.

  It will favor lines in the wall by sampling next direction from
  a distribution favoring previous direction."
  [board-size length]
  ;; random position
  [[(rand-int board-size) (rand-int board-size)]
   ;; random directions length times
   (take length (iterate #(rand-nth (-> [:up :down :left :right] (conj %) (conj %)))
                         (gen/generate (s/gen ::direction))))])

(def wall-generator
  (gen/fmap #(apply generate-wall %)
            (gen/bind (gen/choose min-board-size max-test-board-size)
                    #(gen/tuple (gen/return %) (gen/choose 1 %)))))

(s/def ::wall
  #_("A wall is defined by a starting position and
  directions on which the wall is defined, e.g. 
  [[0 1] [:right :right :up]] describes a wall on [0 1] 
  [0 2] [0 3] and [0 4])")
  (-> (s/tuple ::position (s/coll-of ::direction :min-count 1))
      (s/with-gen (fn [] wall-generator))))

(s/fdef generate-wall
  :args (s/and (s/cat :board-size ::board-size :length (s/int-in 1 max-board-size))
               #(<= (:length %) (:board-size %)))
  :ret ::wall
  :fn (s/and #(= (count (get-in % [:ret 1])) (-> % :args :length))
             #(every? (fn [x] (< x (-> % :args :board-size))) (get-in % [:ret 0]))))


(s/fdef add-wall
  :args (s/and (s/cat :board ::game-board :wall ::wall)
               (fn [args] (every? #(< % (count (-> args :board)))
                                  (get-in args [:wall 0]))))
  :ret ::game-board)
  
(defn add-wall
  "Adds a wall to a board"
  [board [position directions :as wall]]  
  ((reduce (fn [[brd pos] dir] ;; for each direction update board and position
            (let [new-pos (move-position pos dir (count brd))]
              [(assoc-in brd pos :wall) new-pos]))
          [board position]
          (conj directions :up)) 0)) ;; add a dummy direction to add the last wall

(s/fdef add-random-fruit
  :args (-> (s/cat :board ::game-board)
            (s/and (fn [args]
                     (comment "At least 2 empty cells so 1 empty cell remain")
                     (>= (count-cells (:board args) :empty) 2))))
  :ret ::game-board
  :fn (fn [s] (comment "Exactly one more fruit")
        (= (-> s :ret (count-cells :fruit))
           (-> s :args :board (count-cells :fruit) (+ 1)))))

(defn add-random-fruit
  "Adds one fruit at random on the given board"
  [board]
  (let [position (repeatedly 2 #(rand-int (count board)))]
    (if (= (get-in board position) :empty)
      (assoc-in board position :fruit)
      (recur board))))

(defn- board-stats
  [board]
  (let [fc (count-cells board :fruit)
        ec (count-cells board :empty)]
    {:fruit-nb fc
     :non-wall-nb (+ ec fc)
     :fruit-density (-> fc (* 100) (/ (+ fc ec)) int)}))

(s/fdef sow-fruits
  :args (-> (s/cat :board ::game-board :desired-density (s/int-in 1 50))
            (s/and
             (fn [args]
               (comment "actual fruit density should be less than desired density")
               (< (-> args :board board-stats :fruit-density)
                  (-> args :desired-density)))))
  :ret ::game-board
  
  :fn (fn [s] (comment "Checks ratio of fruits on board fits density.")
        (let [{:keys [fruit-nb non-wall-nb]} (-> s :ret board-stats)]
          (<= (/ fruit-nb non-wall-nb)
              (/ (-> s :args :desired-density) 100)
              (/ (inc fruit-nb) non-wall-nb)))))          

(defn sow-fruits
  "Sows fruits on the board randomly according to density percentage :
  the ratio of fruits in cells will be closest to density/100. Density
  should be in [1,50[. Walls are excluded from the count, existing fruits
  on initial board are taken into account in density computation."
  [board desired-density]
  {:pre [(< (-> board board-stats :fruit-density) desired-density)]}
  (let [{:keys [fruit-nb non-wall-nb]} (board-stats board)
        desired-fruit-nb (-> desired-density (* non-wall-nb) (/ 100) int)]
    (-> add-random-fruit
        (iterate board)
        (nth (- desired-fruit-nb fruit-nb)))))
;;;
;;; Player movement
;;;

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
