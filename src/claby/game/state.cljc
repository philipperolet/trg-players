(ns claby.game.state
  "Defines game state--a board, the player's position, a score, a game
  status--and provide utilities to work on it"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.game.board :as gb]))


;; Game state
;;;;;;;;;;;;;

(s/def ::player-position ::gb/position)

(s/def ::score nat-int?)

(s/def ::status #{:active :over :won})

(defn- random-status-generator
  "Generates a random status that fits board specs, notably won status conditions"
  [board]
  (if (zero? (gb/count-cells board :fruit))
    (gen/return :won)
    (gen/such-that #(not= % :won) (s/gen ::status))))
  
(defn- game-state-generator [size]
  (gen/bind
   (gb/game-board-generator size)
   (fn [board]
     (gen/hash-map
      ::gb/game-board (gen/return board)
      ::player-position (gen/vector (gen/choose 0 size) 2)
      ::score (s/gen ::score)
      ::status (random-status-generator board)))))

(s/def ::game-state
  (-> (s/keys :req [::gb/game-board ::player-position ::score ::status])
      (s/and (fn [s] (comment "player position should be inside board")
               (and (< ((s ::player-position) 0) (count (::gb/game-board s)))
                    (< ((s ::player-position) 1) (count (::gb/game-board s)))))
             (fn [s] (comment "player should be on empty space")
               (= (-> s ::gb/game-board (get-in (::player-position s))) :empty))
             (fn [{:keys [::gb/game-board ::status]}]
               (comment "Game is won if and only if no fruits are left.")
               (or (and (= (gb/count-cells game-board :fruit) 0)
                        (= status :won))
                   (and (> (gb/count-cells game-board :fruit) 0)
                        (not= status :won)))))
      (s/with-gen #(gen/bind gb/test-board-size-generator game-state-generator))))

(s/fdef init-game-state
  :args (-> (s/cat :board ::gb/game-board)
            (s/and (fn [args] (comment "At least one fruit otherwise no game")
                     (> (gb/count-cells (:board args) :fruit) 0))))
  :ret ::game-state
  :fn #(= (-> % :args :board) (-> % :ret ::gb/game-board)))

(defn init-game-state
  "Initializes a game state with given board, player at top/center of board
  and null score."
  [board]
  (let [starting-position (->> board count (* 0.5) int (vector 2))]
    {::score 0
     ::status :active
     ::player-position (gb/find-in-board board #{:empty} starting-position)
     ::gb/game-board board}))

;;; Conversion of state to Hiccup HTML
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
  [{:keys [::gb/game-board ::player-position], :as state}]
  (->> game-board
       (map-indexed #(get-html-for-line %1 %2 player-position))
       (concat [:tbody])
       vec))
