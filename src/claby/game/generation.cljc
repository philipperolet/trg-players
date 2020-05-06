(ns claby.game.generation
  "Tools for generating nice boards."
  (:require #?@(:cljs [[clojure.test.check]
                       [clojure.test.check.properties]])
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.game :as g]))

;; Wall generation
;;;;;;;;

(defn generate-wall
  "Generates a random wall of given length for a given board size.

  It will favor lines in the wall by sampling next direction from
  a distribution favoring previous direction."
  [board-size length]
  ;; random position
  [[(rand-int board-size) (rand-int board-size)]
   ;; random directions length times
   (take length (iterate #(rand-nth (-> [:up :down :left :right] (conj %) (conj %)))
                         (gen/generate (s/gen ::g/direction))))])

(def wall-generator
  (gen/fmap #(apply generate-wall %)
            (gen/bind (gen/choose g/min-board-size g/max-test-board-size)
                    #(gen/tuple (gen/return %) (gen/choose 1 %)))))

(s/def ::wall
  #_("A wall is defined by a starting position and
  directions on which the wall is defined, e.g. 
  [[0 1] [:right :right :up]] describes a wall on [0 1] 
  [0 2] [0 3] and [0 4])")
  (-> (s/tuple ::g/position (s/coll-of ::g/direction :min-count 1))
      (s/with-gen (fn [] wall-generator))))

(s/fdef generate-wall
  :args (s/and (s/cat :board-size ::g/board-size
                      :length (s/int-in 1 g/max-board-size))
               #(<= (:length %) (:board-size %)))
  :ret ::wall
  :fn (s/and #(= (count (get-in % [:ret 1])) (-> % :args :length))
             #(every? (fn [x] (< x (-> % :args :board-size))) (get-in % [:ret 0]))))

(s/fdef add-wall
  :args (s/and (s/cat :board ::g/game-board :wall ::wall)
               (fn [args] (every? #(< % (count (-> args :board)))
                                  (get-in args [:wall 0]))))
  :ret ::g/game-board)
  
(defn add-wall
  "Adds a wall to a board"
  [board [position directions :as wall]]  
  ((reduce (fn [[brd pos] dir] ;; for each direction update board and position
            (let [new-pos (g/move-position pos dir (count brd))]
              [(assoc-in brd pos :wall) new-pos]))
          [board position]
          (conj directions :up)) 0)) ;; add a dummy direction to add the last wall


;;; Fruit sowing
;;;;;;;;;

(s/fdef add-random-fruit
  :args (-> (s/cat :board ::g/game-board)
            (s/and (fn [args]
                     (comment "At least 2 empty cells so 1 empty cell remain")
                     (>= (g/count-cells (:board args) :empty) 2))))
  :ret ::g/game-board
  :fn (fn [s] (comment "Exactly one more fruit")
        (= (-> s :ret (g/count-cells :fruit))
           (-> s :args :board (g/count-cells :fruit) (+ 1)))))

(defn add-random-fruit
  "Adds one fruit at random on the given board"
  [board]
  (let [position (repeatedly 2 #(rand-int (count board)))]
    (if (= (get-in board position) :empty)
      (assoc-in board position :fruit)
      (recur board))))

(s/fdef sow-fruits
  :args (-> (s/cat :desired-density (s/int-in 1 50) :board ::g/game-board)
            (s/and
             (fn [args]
               (comment "actual fruit density should be less than desired density")
               (< (-> args :board g/board-stats :fruit-density)
                  (-> args :desired-density)))))
  :ret ::g/game-board
  
  :fn (fn [s] (comment "Checks ratio of fruits on board fits density.")
        (let [{:keys [fruit-nb non-wall-nb]} (-> s :ret g/board-stats)]
          (<= (/ fruit-nb non-wall-nb)
              (/ (-> s :args :desired-density) 100)
              (/ (inc fruit-nb) non-wall-nb)))))          

(defn sow-fruits
  "Sows fruits on the board randomly according to density percentage :
  the ratio of fruits in cells will be closest to density/100. Density
  should be in [1,50[. Walls are excluded from the count, existing fruits
  on initial board are taken into account in density computation."
  [desired-density board]
  {:pre [(< (-> board g/board-stats :fruit-density) desired-density)]}
  (let [{:keys [fruit-nb non-wall-nb]} (g/board-stats board)
        desired-fruit-nb (-> desired-density (* non-wall-nb) (/ 100) int)]
    (-> add-random-fruit
        (iterate board)
        (nth (- desired-fruit-nb fruit-nb)))))

;;; Nice board
;;;;;;

(s/fdef create-nice-board
  :args (-> (s/cat :size ::g/board-size)
            (s/with-gen #(gen/vector g/test-board-size-generator 1)))
  :ret ::g/game-board)

(defn create-nice-board
  "Creates a board with walls and fruits that looks well. It adds as much random
  walls as the size of the board, favoring walls of length ~ size/2 so about half
  the board is walled."
  [size]
  (let [nb-of-walls (int (/ size 2))
        rand-wall-length ;; generates a length biased towards average-sized walls
        (fn [] (int (/ (reduce + (repeatedly 5 #(inc (rand-int (dec size))))) 5)))
        add-random-wall
        #(add-wall % (generate-wall size (rand-wall-length)))]
    
    (->> (::g/game-board (g/init-game-state (g/empty-board size)))
         (iterate add-random-wall)
         (#(nth % nb-of-walls))
         (sow-fruits 5))))