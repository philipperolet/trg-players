(ns claby.game
  "The game is about eating fruits in a maze.
  This namespace defines board & game state and provides utilities to count
  stuff and move on the board."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;;; Game board
;;;;;;;;;;;;;;

(defonce min-board-size 5)
(defonce max-board-size 100)
(defonce max-test-board-size 20)

(s/def ::board-size (s/int-in min-board-size max-board-size))

(defonce game-cell-values #{:empty :wall :fruit :cheese})
(s/def ::game-cell game-cell-values)

(s/def ::game-line (s/every ::game-cell
                            :kind vector?
                            :min-count min-board-size
                            :max-count max-board-size
                            :gen-max max-test-board-size))

(def test-board-size-generator (gen/choose min-board-size max-test-board-size))

(defn- game-board-generator [size]
  "Returns a board generator favoring empty cells (more than half)"
  (-> (gen/one-of [(s/gen ::game-cell) (gen/return :empty)])
      (gen/vector size)
      (gen/vector size)))

(def test-board-generator
  (gen/bind test-board-size-generator game-board-generator))

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
      
      (s/with-gen (fn [] test-board-generator))))

(s/fdef count-cells
  :args (s/cat :board ::game-board :value ::game-cell)
  :ret nat-int?
  :fn (fn [s] (comment "Less than the total number of cells")
        (<= (:ret s) (reduce * (repeat 2 (-> s :args :board count))))))

(defn count-cells
  "Number of cells with given value on given board"
  [board value]
  (->> board (reduce into) (filter #(= % value)) count))

(s/fdef empty-board
  :args (-> (s/cat :size ::board-size)
            (s/with-gen #(gen/vector test-board-size-generator 1)))
  :ret ::game-board
  :fn #(= (count-cells (:ret %) :empty) (reduce * (repeat 2 (-> % :args :size)))))

(defn empty-board
  [size]
  (vec (repeat size (vec (repeat size :empty)))))

(defn board-stats
  [board]
  (let [walls (count-cells board :wall) total-cells (* (count board) (count board))]

    {:total-cells total-cells

     :non-wall-cells (- total-cells walls)

     :density ;; map of efach element's density on board (except wall)     
     (let [elts (remove #{:wall :empty} game-cell-values)
           compute-density
           #(-> (count-cells board %) (* 100) (/ (- total-cells walls)) int)]
       (zipmap elts (map compute-density elts)))}))

;;; For generic version, f should take as arg an element of coll and
;;; return an int, but specing that makes some valid functions not
;;; conform, e.g. `(first`. Thus no spec is given and a dummy
;;; generator is provided)")
(s/fdef get-closest
  :args (s/alt :ints (-> (s/cat :coll (s/coll-of int?)
                                :i int?)
                         ;; generator provided to avoid integer overflow
                         (s/with-gen #(gen/tuple (gen/vector (gen/choose -100 100))
                                                 (gen/choose -100 100))))
               :generic (s/cat :coll (s/coll-of any?)
                               :f (-> any? 
                                      (s/with-gen #(gen/return (fn [x] 0))))
                               :i int?))
  :ret any?
  :fn (fn [sp] (comment "Result actually belongs to collection")
        (or (nil? (:ret sp))
            (some #(= (:ret sp) %) (get-in sp [:args 1 :coll])))))

(defn get-closest 
  "Retrieves the element elt such that f(elt) is closest to i in coll.
  Returns nil for empty colls."
  ([coll f i]
   (when-not (empty? coll)
     (apply min-key #(- (max (f %) i) (min (f %) i)) coll)))
  ([coll i]
   (get-closest coll identity i)))

(s/fdef find-in-board
  :args (-> (s/cat :board ::game-board
                   :cell-set (s/coll-of ::game-cell :kind set?)
                   :position (s/? ::position))
            (s/with-gen (fn [] (gen/tuple
                                test-board-generator
                                (gen/one-of [(gen/return #{:fruit})
                                             (gen/return #{:wall})
                                             (gen/return #{:empty})])))))
  :ret (s/or :pos ::position :nil nil?)
  :fn #(or nil?
           (apply (-> % :args :cell-set)
                  (list (get-in (-> % :args :board) (:ret %))))))

(defn find-in-board
  "Finds the closest cell in board whose value is in cell-set,
  traversing the board from the given position (defaults to [0 0]),
  line by line. Returns nil if no result."
  ([board cell-set position]
   (->> board
       (keep-indexed
        (fn [ind line] ;; gets the first matching position in this line
          (->> line
               (keep-indexed #(when (cell-set %2) %1))
               (#(get-closest % (position 1)))
               (#(if % (vector ind %))))))
       (#(get-closest % first (position 0)))))
  
  ([board cell-set]
   (find-in-board board cell-set [0 0])))

;; Game state
;;;;;;;;;;;;;

(s/def ::position
  (s/tuple (s/int-in 0 max-board-size)
           (s/int-in 0 max-board-size)))

(s/def ::player-position ::position)

(s/def ::score nat-int?)

(s/def ::status #{:active :over :won})

(defn- random-status-generator
  "Generates a random status that fits board specs, notably won status conditions"
  [board]
  (if (zero? (count-cells board :fruit))
    (gen/return :won)
    (gen/such-that #(not= % :won) (s/gen ::status))))
  
(defn- game-state-generator [size]
  (gen/bind
   (game-board-generator size)
   (fn [board]
     (gen/hash-map
      ::game-board (gen/return board)
      ::player-position (gen/vector (gen/choose 0 size) 2)
      ::score (s/gen ::score)
      ::status (random-status-generator board)))))

(s/def ::game-state
  (-> (s/keys :req [::game-board ::player-position ::score ::status])
      (s/and (fn [s] (comment "player position should be inside board")
               (and (< ((s ::player-position) 0) (count (::game-board s)))
                    (< ((s ::player-position) 1) (count (::game-board s)))))
             (fn [s] (comment "player should be on empty space")
               (= (-> s ::game-board (get-in (::player-position s))) :empty))
             (fn [{:keys [::game-board ::status]}]
               (comment "Game is won if and only if no fruits are left.")
               (or (and (= (count-cells game-board :fruit) 0)
                        (= status :won))
                   (and (> (count-cells game-board :fruit) 0)
                        (not= status :won)))))
      (s/with-gen #(gen/bind test-board-size-generator game-state-generator))))

(s/fdef init-game-state
  :args (-> (s/cat :board ::game-board)
            (s/and (fn [args] (comment "At least one fruit otherwise no game")
                     (> (count-cells (:board args) :fruit) 0))))
  :ret ::game-state
  :fn #(= (-> % :args :board) (-> % :ret ::game-board)))

(defn init-game-state
  "Initializes a game state with given board, player at top/center of board
  and null score."
  [board]
  (let [starting-position (->> board count (* 0.5) int (vector 2))]
    {::score 0
     ::status :active
     ::player-position (find-in-board board #{:empty} starting-position)
     ::game-board board}))

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
  :args (-> (s/cat :state ::game-state :direction ::direction)
            (s/and #(= (-> % :state ::status) :active)))
  :ret ::game-state)

(defn move-player
  "Moves player according to provided direction on given state: returns
  state with updated player-position and board."
  [{:keys [::game-board ::player-position], :as state} direction]
  (let [new-position (move-position player-position direction (count game-board))]
    (case (get-in game-board new-position) ;; depending on where player wants to move
      :wall state ;; move fails
      
      :empty (assoc state ::player-position new-position) ;; move occurs
      
      :fruit (-> state ;; move occurs and score increases, possibly winning
                 (assoc ::player-position new-position)
                 (update ::score inc)
                 (assoc-in (into [::game-board] new-position) :empty)
                 (#(if (= (count-cells (% ::game-board) :fruit) 0)
                     (assoc % ::status :won) %)))
      
      :cheese (-> state ;; game over if eats cheese
                  (assoc ::player-position new-position) 
                  (assoc-in (into [::game-board] new-position) :empty)
                  (assoc ::status :over)))))

(defn move-player-path
  [state directions]
  "Moves player repeatedly on the given collection of directions"
  (reduce move-player state directions))


;;; Conversion to Hiccup HTML
;;;;

(s/fdef get-html-for-state
  :args (s/cat :state ::game-state)
  :ret  (s/and vector?
               #(= (first %) :tbody)))

(defn- get-html-for-cell
  "Generates html for a game board cell"
  [cell-index cell player-position]
  (-> (str "td." (name cell) (if (= player-position cell-index) ".player"))
      keyword
      vector
      (conj {:key (str "claby-" (cell-index 0) "-" (cell-index 1))})))

(defn- get-html-for-line
  "Generates html for a game board row"
  [row-index row player-position]
  (->> row ; for each cell of the row
       (map-indexed #(get-html-for-cell [row-index %1] %2 player-position))
       (concat [:tr {:key (str "claby-" row-index)}])
       vec))

(defn get-html-for-state
  "Given a game state, generates the hiccup html to render it with reagent.

  E.g. for a game board [[:empty :empty] [:wall :fruit]] with player
  position [0 1] it should generate
  [:table [:tr [:td.empty] [:td.empty.player]] [:tr [:td.wall] [:td.fruit]]]"
  [{:keys [::game-board ::player-position], :as state}]
  (->> game-board
       (map-indexed #(get-html-for-line %1 %2 player-position))
       (concat [:tbody])
       vec))
