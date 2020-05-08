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

(s/fdef add-random-element
  :args (-> (s/cat :board ::g/game-board :element (s/and ::g/game-cell #(not= % :empty)))
            (s/and (fn [args]
                     (comment "At least 2 empty cells so 1 empty cell remain")
                     (>= (g/count-cells (:board args) :empty) 2))))
  :ret ::g/game-board
  :fn (fn [{:keys [ret] {:keys [board element]} :args}]
        (comment "Exactly one more element")
        (= (g/count-cells ret element)
           (inc (g/count-cells board element)))))

(defn add-random-element
  "Adds one element at random on the given board"
  [board element]
  (let [position (repeatedly 2 #(rand-int (count board)))]
    (if (= (get-in board position) :empty)
      (assoc-in board position element)
      (recur board element))))


(s/fdef sow
  :args (-> (s/cat :board ::g/game-board
                   :element (s/and ::g/game-cell (complement #{:empty :wall}))
                   :quantity nat-int?)
            (s/and
             (fn [args]
               (comment "quantity < empty cells so at least an empty cell remains")
               (let [{:keys [total-cells]} (-> args :board g/board-stats)]
                 (< (-> args :quantity) (-> args :board (g/count-cells :empty)))))))

  :ret ::g/game-board
  
  :fn (fn [{:keys [ret] {:keys [element quantity board]} :args}]
        (comment "New element count should match quantity + previous fruit count")
        (= (g/count-cells ret element) (+ quantity (g/count-cells board element)))))

(defn sow
  "Sows element on board randomly, quantity times. Existing fruits
  on initial board are not taken into account: total number
  of fruits is = to quantity + previous fruit nb after the call."
  [board element quantity]
  {:pre [(<= (+ (g/count-cells board element) quantity) (-> board g/board-stats :non-wall-cells))]}
  (if (= 0 quantity)
    board
    (recur (add-random-element board element) element (dec quantity))))

      
(s/def ::element-density (-> (s/int-in 0 99)
                             (s/with-gen #(gen/choose 0 30))))

(defn valid-density
  "Checks the density of element is within normal bounds for board"
  [board element density]
  (let [elt-cells (g/count-cells board element)
              non-wall-cells (-> board g/board-stats :non-wall-cells)]
          (<= (/ elt-cells non-wall-cells)
              (/ density 100)
              (/ (inc elt-cells) non-wall-cells))))

(defn sum-of-densities
  [board]
  (reduce + (vals (:density (g/board-stats board)))))
                      
(s/fdef sow-by-density
  :args (-> (s/cat :board ::g/game-board
                   :element (s/and ::g/game-cell  (complement #{:wall :empty}))
                   :desired-density ::element-density)
            (s/and
             (fn [{:keys [board element desired-density]}]
               (comment "actual density should be less than desired density")
               (< (-> board g/board-stats :density element)
                  desired-density))
             (fn [{:keys [board element desired-density]}]
               (comment "sum of densities should be < 100")
               (< (-> (sum-of-densities board)
                      (- (-> board g/board-stats :density element))
                      (+ desired-density))
                  100))))

  :ret ::g/game-board
  
  :fn (fn [{ret :ret, {:keys [element desired-density]} :args}]
        (comment "Checks ratio of element on board fits density.")
        (valid-density ret element desired-density)))

(defn sow-by-density
  "Sows fruits on the board randomly according to density percentage :
  the ratio of fruits in cells will be closest to density/100. Density
  should be in [1,50[. Walls are excluded from the count, existing fruits
  on initial board are taken into account in density computation."
  [board element desired-density]
  {:pre [(<= (-> board g/board-stats :density element) desired-density)]}
  (let [non-wall-cells ((g/board-stats board) :non-wall-cells)
        desired-nb (-> desired-density (* non-wall-cells) (/ 100) int)]
    (sow board element (- desired-nb (g/count-cells board element)))))

;;; Nice board
;;;;;;

;; map of densities for non-walls (and non-empty)
(s/def ::density-map (s/map-of (s/and ::g/game-cell (complement #{:wall :empty}))
                               ::element-density
                               :distinct true))

(s/fdef create-nice-board
  :args (-> (s/cat :size ::g/board-size
                   :level (s/keys :req [::density-map]))
            (s/with-gen #(gen/tuple
                          g/test-board-size-generator
                          (gen/hash-map ::density-map (s/gen ::density-map)))))
  :ret ::g/game-board)

(defn create-nice-board
  "Creates a board with walls and fruits that looks well. It adds as much random
  walls as the size of the board, favoring walls of length ~ size/2 so about half
  the board is walled."
  [size level]
  (let [nb-of-walls (int (/ size 2))
        rand-wall-length ;; generates a length biased towards average-sized walls
        (fn [] (int (/ (reduce + (repeatedly 5 #(inc (rand-int (dec size))))) 5)))
        add-random-wall
        #(add-wall % (generate-wall size (rand-wall-length)))]
    
    (-> (g/empty-board size)
        (#(iterate add-random-wall %))
        (nth nb-of-walls)
        (#(reduce-kv sow-by-density % (-> level ::density-map))))))
