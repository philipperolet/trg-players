(ns mzero.ai.players.m0-modules.m00-reinforcement
  "Reinforcement logic for m00 player, via main function `backward-pass`"
  (:require [clojure.spec.alpha :as s]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.common :as mzc]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.players.m0-modules.motoneurons :as mzm]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]))

(defn- keep-nil-move-at-zero
  "In target distribution, update the nil motoneuron target probability
  to be equal to the probability computed by the network.

  It guarantees weights for the nil motoneuron will not be
  affected by backprop, and will all remanin at 0, so the nil
  motoneuron's value is always 0. This is the `nil-baseline`
  heuristic, see doc"
  [target-distribution motoneurons label-distribution-fn]
  (assoc (vec target-distribution) (mzm/motoneuron-index nil)
         (nth (label-distribution-fn motoneurons) (mzm/motoneuron-index nil))))

(defn- reinforce-movement-distribution
  "Return a distribution for backprop with CEL that will favor
  `movement`, in a sound fashion (see version notes on sound target
  distribution)."
  [movement motoneurons label-distribution-fn]
  (-> (repeat mzm/motoneuron-number 0.0)
      vec
      (assoc (mzm/motoneuron-index movement) 1.0)
      (keep-nil-move-at-zero motoneurons label-distribution-fn)))

(defn- penalize-movement-distribution
  "Return a distribution for backprop with CEL that will penalize
  `movement`, in a sound fashion (see version notes on sound target
  distribution)."
  [movement motoneurons label-distribution-fn]
  (-> (assoc motoneurons (mzm/motoneuron-index movement) ##-Inf)
      label-distribution-fn
      (keep-nil-move-at-zero motoneurons label-distribution-fn)))

(defn- reinforce
  [{:as player :keys [ann-impl next-movement]}]
  (update player :ann-impl mzann/backward-pass!
          [(reinforce-movement-distribution next-movement
                                            (mzm/motoneuron-values player)
                                            (:label-distribution-fn ann-impl))]))

(defn- penalize
  [{:as player :keys [ann-impl next-movement]}]
  (update player :ann-impl mzann/backward-pass!
          [(penalize-movement-distribution
            next-movement
            (mzm/motoneuron-values player)
            (:label-distribution-fn ann-impl))]))

(defn backward-pass
  "If `player` should be rewarded or penalized, performs a backward-pass!"
  [player game-state game-step]
  (let [data (-> player ::mzs/senses ::mzs/data)
        score-increase?
        (< (-> data ::mzs/previous-score) (-> game-state ::gs/score))
        player-didnt-move?
        (= (-> data ::mzs/last-position) (-> game-state ::gs/player-position))]
    (cond
      (zero? game-step) player ;; no backprop at step 0 of the game-> do nothing
      score-increase? (reinforce player)
      player-didnt-move? (penalize player)
      :else player)))
