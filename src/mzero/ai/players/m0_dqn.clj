(ns mzero.ai.players.m0-dqn
  (:require [mzero.ai.player :as aip]
            [mzero.ai.players.base :as mzb]
            [mzero.game.state :as gs]
            [mzero.ai.world :as aiw]
            [clojure.data.generators :as g]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.ai.players.m0-modules.motoneurons :as mzm]
            [mzero.ai.ann.ann :as mzann]
            [uncomplicate.commons.core :refer [release Releaseable]]
            [mzero.ai.ann.losses :as mzl]))

(def replay-batch-size 4)

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
  "At index 0 of `previous-datapoints`, the current input
  vector (without action/reward)"
  [previous-datapoints index]
  (let [{:keys [::mzs/reward ::mzs/action] input-vector-t ::mzs/state}
        (nth previous-datapoints (inc index))
        input-vector-tp1 (::mzs/state (nth previous-datapoints index))
        state-t
        (->> input-vector-t
             (mzs/stm-input-vector (drop (+ 2 index) previous-datapoints))
             (reduce into))
        state-tp1
        (->> input-vector-tp1
             (mzs/stm-input-vector (drop (inc index) previous-datapoints))
             (reduce into))]
    {:st state-t :at action :rt reward :st1 state-tp1}))

(defn- replay-batch
  "Replay batch sampling. `current-input-vector` is appended to previous
  datapoints as a possible state t+1 (but cannot be a state t since
  its associated reward not known."
  ([previous-datapoints current-input-vector]
   (let [datapoints-range
         (- (count previous-datapoints) mzs/short-term-memory-length)
         previous-dps-with-current-iv
         (cons {::mzs/state current-input-vector} previous-datapoints)]
     (when (>= datapoints-range replay-batch-size)
       (map (partial pick-datapoint previous-dps-with-current-iv)
            (g/reservoir-sample replay-batch-size (range datapoints-range))))))
  ([{:as senses :keys [::mzs/input-vector ::mzs/data]}]
   (replay-batch (::mzs/previous-datapoints data) input-vector)))

(defn- create-target-tensor
  [ann-impl batch]
  (let [gamma 0.98
        target-values
        (->> (mzann/forward-pass! ann-impl (mapv :st1 batch))
             mzann/output
             (map #(apply max %))
             (map #(* gamma %))
             (map + (mapv :rt batch)))
        create-target-vector
        (fn [action target-value]
          (-> (repeat mzm/motoneuron-number nil)
              vec
              (assoc (mzm/motoneuron-index action) target-value)))]
    (mapv create-target-vector (mapv :at batch) target-values)))

(defn- backpropagate
  [ann-impl batch]
  (let [input-tensor (mapv :st batch)
        target-tensor (create-target-tensor ann-impl batch)
        discount-factor (repeat (count batch) 1.0)]
    (mzann/backward-pass! ann-impl input-tensor target-tensor discount-factor)))

(defn- train-player [player]
  (if-let [batch (replay-batch (-> player ::mzs/senses))]
    (update player :ann-impl backpropagate batch)
    player))

(defrecord M0DqnPlayer []
  aip/Player
  (init-player [player opts world]
    (let [opts-with-mse-loss
          (assoc-in opts [:ann-impl :loss-gradient-fn] mzl/mse-loss-gradient)]
      (-> (mzb/initialize-player player opts-with-mse-loss world)
          (assoc :epsilon 1.0))))

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
