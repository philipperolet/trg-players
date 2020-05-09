(ns claby.game.events
  "Defines game events such as player movement."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.game.board :as gb]
            [claby.game.state :as gs]))

;;; Movement on board
;;;;;;;

(s/def ::direction #{:up :right :down :left})

(s/fdef move-position
  :args (s/cat :position ::gb/position
               :direction ::direction
               :board-size ::gb/board-size)
  :ret ::gb/position)

(defn move-position
  "Given a board position, returns the new position when moving in
  provided direction, by adding direction to position modulo size of board"
  [position direction board-size]
  (->> (case direction :up [-1 0] :right [0 1] :down [1 0] :left [0 -1])
       (map #(mod (+ %1 %2) board-size) position)
       vec))

(s/fdef move-player
  :args (-> (s/cat :state ::gs/game-state :direction ::direction)
            (s/and #(= (-> % :state ::gs/status) :active)))
  :ret ::gs/game-state)

(defn move-player
  "Moves player according to provided direction on given state: returns
  state with updated player-position and board."
  [{:keys [::gb/game-board ::gs/player-position], :as state} direction]
  (let [new-position (move-position player-position direction (count game-board))]
    (case (get-in game-board new-position) ;; depending on where player wants to move
      :wall state ;; move fails
      
      :empty (assoc state ::gs/player-position new-position) ;; move occurs
      
      :fruit (-> state ;; move occurs and score increases, possibly winning
                 (assoc ::gs/player-position new-position)
                 (update ::gs/score inc)
                 (assoc-in (into [::gb/game-board] new-position) :empty)
                 (#(if (= (gb/count-cells (% ::gb/game-board) :fruit) 0)
                     (assoc % ::gs/status :won) %)))
      
      :cheese (-> state ;; game over if eats cheese
                  (assoc ::gs/player-position new-position) 
                  (assoc-in (into [::gb/game-board] new-position) :empty)
                  (assoc ::gs/status :over)))))

(defn move-player-path
  [state directions]
  "Moves player repeatedly on the given collection of directions"
  (reduce move-player state directions))

