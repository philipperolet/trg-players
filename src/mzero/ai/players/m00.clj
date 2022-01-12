(ns mzero.ai.players.m00
  "M00 takes a subset of the environment's current state as input and
  feeds it to a neural network of densely connected layers. It outputs
  logprobabilities for each possible move, and the actual move is
  drawn according to the probability distribution.

  Positive reinforcement is provided when a fruit is eaten: the move
  that led to eating the fruit is used as the target class and
  backpropagation occurs.

  Similarly, negative reinforcement happens when the player bumps in a
  wall (i.e. tries to move on a wall cell)."
  (:require [clojure.data.generators :as g]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.player :as aip]
            [mzero.ai.players.base :as mzb]
            [mzero.ai.players.m0-modules.m00-reinforcement :as m00r]
            [mzero.ai.players.m0-modules.motoneurons :as mzm]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.ai.ann.losses :as mzl]
            [mzero.ai.world :as aiw]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.utils.utils :as u]
            [uncomplicate.commons.core
             :refer
             [release Releaseable]]
            [mzero.ai.ann.label-distributions :as mzld]))

(defn- reflex-movement
  "Random move reflex : Reflex to move randomly when player has not moved for a
  while--relying on motoception so the while length is defined by
  motoception-persistence."
  [player]
  (when (zero? (mzs/motoception (-> player ::mzs/senses ::mzs/input-vector)))
    (nth ge/directions (-> (:rng player) (. nextInt) (mod 4)))))

(defn next-direction
  "Pick a direction according to motoneuron values and a label
  distribution function. 0.0 is conjed to motoneuron values to
  represent a no-movement class that has some probability of occuring
  provided label-distribution-fn is not 0 in 0 -- which it shouldn't be"
  [motoneurons label-distribution-fn]
  (u/weighted-rand-nth mzm/movements
                       (label-distribution-fn motoneurons)))

(defn- make-move
  [{:as player {:keys [label-distribution-fn]} :ann-impl}]
  (assoc player :next-movement
         (or (reflex-movement player)
             (-> player mzm/motoneuron-values
                 (next-direction label-distribution-fn)))))

(defn- select-action [player]
  (let [flattened-input-matrix
        (reduce into (mzs/stm-input-vector (-> player ::mzs/senses)))]
    (-> player
        (update :ann-impl mzann/forward-pass! [flattened-input-matrix])
        make-move)))

(def m00-ann-default-opts {:label-distribution-fn mzld/ansp})

(defrecord M00Player []
  aip/Player
  (init-player [player opts world]
    (let [opts-with-m00-ann-defaults
          (update opts :ann-impl #(merge m00-ann-default-opts %))
          label-distribution-fn
          (-> opts-with-m00-ann-defaults :ann-impl :label-distribution-fn)
          opts-with-ce-loss
          (assoc-in opts-with-m00-ann-defaults [:ann-impl :loss-gradient-fn]
                    (partial mzl/cross-entropy-loss-gradient label-distribution-fn))]
      (mzb/initialize-player player opts-with-ce-loss world)))

  (update-player [player {:as world, :keys [::gs/game-state ::aiw/game-step]}]
    (binding [g/*rnd* (-> player :rng)]
      (-> player
          (mzb/record-measure world)
          (update ::mzs/senses mzs/update-senses world player)
          (m00r/backward-pass game-state game-step)
          select-action)))
  
  Releaseable
  (release [{:keys [ann-impl]}]
    (release ann-impl)))
