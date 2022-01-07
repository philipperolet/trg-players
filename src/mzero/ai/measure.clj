(ns mzero.ai.measure
  (:require [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.game.board :as gb]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]))

(defn elt-in-direction?
  [elt position game-board direction]
  (->> (ge/move-position position direction (count game-board))
       (get-in game-board)
       (= elt)))

(defn elt-1-step-away?
  [elt position game-board]
  (some #(elt-in-direction? elt position game-board %)
        ge/directions))

(defn elt-2-steps-away?
  [elt position game-board]
  (and (not (elt-1-step-away? elt position game-board))
       (some #(elt-1-step-away? elt % game-board)
             (map #(ge/move-position position % (count game-board)) ge/directions))))

(defn update-nb-moved-wall
  [player
   {:as world {:keys [::gs/player-position ::gb/game-board]} ::gs/game-state}]
  (let [previous-position
        (-> player ::mzs/senses ::mzs/data ::gs/game-state ::gs/player-position)
        increase-if-moved-wall
        (fn [nb] ;; position didn't change and wall in requested move direction
          (cond-> (or nb 0)
            (and (= player-position previous-position)
                 (when-let [mv (-> player :next-movement)]
                   (elt-in-direction? :wall player-position game-board mv)))
            inc))]
    (update-in player [:step-measurements :nb-moved-wall] increase-if-moved-wall)))

(defn step-measure
  "Measurement to add to player's `:step-measurements` at every step
  during a game"
  [{:as world {:keys [::gs/player-position ::gb/game-board ::gs/score]} ::gs/game-state}
   player]
  (let [increase-if-next-to
        (fn [nb elt]
          (cond-> (or nb 0)
            (elt-1-step-away? elt player-position game-board)
            inc))
        increase-if-2steps-to
        (fn [nb elt]
          (cond-> (or nb 0)
            (elt-2-steps-away? elt player-position game-board)
            inc))
        previous-score
        (or (-> player ::mzs/senses ::mzs/data ::gs/game-state ::gs/score) 0)
        decrease-on-score-raise
        (fn [nb]
          (cond-> (or nb 0)
            (and (> score previous-score) (pos? (or nb 0)))
            dec))]
    (-> player
        (update-in [:step-measurements :nb-next-fruit] increase-if-next-to :fruit)
        (update-in [:step-measurements :nb-2step-fruit] increase-if-2steps-to :fruit)
        (update-in [:step-measurements :nb-next-wall] increase-if-next-to :wall)
        (update-in [:step-measurements :nb-next-fruit2] increase-if-next-to :fruit)
        (update-in [:step-measurements :nb-next-fruit2] decrease-on-score-raise)
        (update-nb-moved-wall world))))

(defn game-measure
  "Measurement to add to player's `:game-measurements` at every game"
  [{:as world {:keys [::gs/score]} ::gs/game-state}
   {:as player {:keys [nb-next-fruit nb-2step-fruit nb-next-wall nb-moved-wall]} :step-measurements}]
  {:score score
   :fruit-move-ratio (when (pos? nb-next-fruit) ;; avoid division by 0
                       (float (/ score nb-next-fruit)))
   :wall-move-ratio (when (pos? nb-next-wall)
                      (float (/ nb-moved-wall nb-next-wall)))
   :fem2-ratio (when (pos? nb-2step-fruit) ;; avoid division by 0
                 (float (/ nb-next-fruit nb-2step-fruit)))})

