(ns mzero.ai.players.m0-dqn
  (:require [mzero.ai.player :as aip]
            [mzero.ai.players.base :as mzb]
            [mzero.game.state :as gs]
            [mzero.ai.world :as aiw]
            [clojure.data.generators :as g]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.ai.players.m0-modules.motoneurons :as mzm]
            [mzero.ai.ann.ann :as mzann]
            [uncomplicate.commons.core :refer [release Releaseable]]))

(def replay-batch-size 10)

(defn- greedy-action [player]
  (let [flattened-input-matrix
        (reduce into (mzs/stm-input-vector (-> player ::mzs/senses)))
        max-value-movement
        (fn [values]
          (apply max-key (zipmap mzm/movements values) mzm/movements))]
    (-> (update player :ann-impl mzann/forward-pass! [flattened-input-matrix])
        mzm/motoneuron-values
        max-value-movement)))

(defn- epsilon-decay
  "Decrease epsilon inversly to number of training steps Start at 1,
  reaches 0.1 after 5M steps, stops decay after reaching 0.05"
  [epsilon]
  (let [min-epsilon 0.05
        epsilon-decay 0.9999995394830874]
    (max min-epsilon (* epsilon epsilon-decay))))

(defn- select-action
  "Epsilon-greedy action selection policy"
  [player]
  (let [random-action? (< (g/float) (-> player :epsilon))]
    (-> player
        (assoc :next-movement (if random-action?
                                (g/rand-nth mzm/movements)
                                (greedy-action player)))
        (update :epsilon epsilon-decay))))

(defn- pick-datapoint
  [previous-datapoints index]
  (let [new-input
        (->> (drop index previous-datapoints)
             (map ::mzs/state)
             (take mzs/short-term-memory-length)
             vec
             (reduce into))
        {:keys [::mzs/reward ::mzs/action]} (nth previous-datapoints index)
        new-target
        (cond
          (pos? reward)
          (-> (repeat mzm/motoneuron-number nil)
              vec
              (assoc (mzm/motoneuron-index action) 1.0))
          
          (neg? reward)
          (-> (repeat mzm/motoneuron-number nil)
              vec
              (assoc (mzm/motoneuron-index action) 0.0)))]
    {:rb-inputs new-input :rb-targets new-target}))

(defn- replay-batch
  [previous-datapoints]
  (let [datapoints-range
        (- (count previous-datapoints) mzs/short-term-memory-length)]
    (when (>= datapoints-range replay-batch-size)
      (map (partial pick-datapoint previous-datapoints)
           (g/reservoir-sample replay-batch-size (range datapoints-range))))))

(defn- train-player [player]
  (let [{:keys [rb-inputs rb-targets]}
        (->> (replay-batch (-> player ::mzs/senses ::mzs/data ::mzs/previous-datapoints))
             (remove #(nil? (:rb-targets %)))
             (apply merge-with conj {:rb-inputs nil :rb-targets nil}))
        df (repeat (count rb-targets) 1.0)]
    (cond-> player
      rb-targets
      (update :ann-impl mzann/backward-pass! rb-inputs rb-targets df))))

(defrecord M0DqnPlayer []
  aip/Player
  (init-player [player opts world]
    (-> (mzb/initialize-player player opts world)
        (assoc :epsilon 1.0)))

  (update-player [player {:as world, :keys [::gs/game-state ::aiw/game-step]}]
    (binding [g/*rnd* (-> player :rng)]
      (-> player
          (mzb/record-measure world)
          (update ::mzs/senses mzs/update-senses world player)
          train-player
          select-action)))
  
  Releaseable
  (release [{:keys [ann-impl]}]
    (release ann-impl)))
