(ns mzero.ai.train
  "Module with training utilities for the m00 player.

  Specific options include `batch-size`, the number of players & games
  played simultaneously, maxed to the max batch size for an ANN."
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.shallow-mpann :as mzsmp]
            [mzero.ai.measure :as mzme]
            [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw]
            [mzero.utils.random :refer [seeded-seeds]]
            [mzero.utils.utils :as u]
            [mzero.ai.players.m00 :as m00]
            [mzero.ai.game-runner :as gr]
            [mzero.ai.players.base :as mzb]))

(def nb-steps-per-game 5000)
(def default-board-size 30)

(s/def ::batch-size (s/int-in 1 (inc mzann/max-batch-size)))

(defn- unwrap [seq_] (cond-> seq_ (= 1 (count seq_)) first))

(defn initial-shallow-players
  [{:as opts :keys [batch-size]} worlds]
  ;; mimic the seed used in M00Player for ANN
  (let [seed (. (java.util.Random. (:seed opts)) nextInt)
        ann-impl (#'mzb/initialize-ann-impl opts seed)]
    (map #(-> (aip/load-player "m00" (assoc opts :ann-impl %1) %2)
              (assoc :game-measurements []))
         (mzsmp/shallow-mpanns batch-size ann-impl)
         (take batch-size worlds))))

(defn initial-m00-players
  [{:as opts :keys [batch-size] :or {batch-size 1}} worlds]
  (map #(-> (aip/load-player "m00" opts %)
            (assoc :game-measurements []))
       (take batch-size worlds)))

(defn- run-single-steps-in-parallel!
  "Given a list of pairs of world/player atoms, run 1 step for every
  game pair in parallel.

  If a game is over, a new game with a newly generated (seeded random)
  world will be started for the player, and so on until
  `nb-steps-per-game` are exhausted.

  Meaningless return value."
  [game-pairs]
  (let [run-opts {:number-of-steps 1 :logging-steps 0}
        game-runner (fn [w p] (gr/->MonoThreadRunner w p run-opts))
        next-world ;; create a new world if the player finishes
        (fn [p] (aiw/world default-board-size (.nextInt (:rng p))))
        run-single-step
        (fn [{:as game-pair :keys [world player]}]
          (when (not (aiw/active? @world))
            (reset! world (next-world @player)))
          (gr/run-game (game-runner world player)))]
    (->> (mapv #(future (run-single-step %)) game-pairs)
         (mapv deref))))

(defn- run-and-measure-game-batch
  "Run a batch of games simultaneously at every step, as opposed to
  running a game until it ends, then the next game, etc. This is to
  allow batch learning on an ANN. Order of players / worlds is
  preserved.

  Return players with appropriate measurements added to
  players. In-game step measurements are taken via `step-measure` and
  averages on a game via `game-measure`.
  
  There should be the same number of `players` and `worlds`"
  [players worlds]
  (assert (= (count worlds) (count players)))
  (u/with-loglevel java.util.logging.Level/WARNING
    (let [game-pairs (map #(hash-map :player %1 :world %2)
                          (map atom players)
                          (map atom worlds))
          add-measurement-to-game-pair
          (fn [{:as game-pair :keys [world player]}]
            (-> (update-in game-pair [:player :game-measurements]
                           conj (mzme/game-measure world player))
                (update :player dissoc :step-measurements)))]
      (dotimes [_ nb-steps-per-game] (run-single-steps-in-parallel! game-pairs))
      (->> (mapv #(u/map-map deref %) game-pairs)
           (map add-measurement-to-game-pair)
           (mapv :player)))))

(defn- run-games-base
  "Run `nb-games` on boards of size 30 with an m00 player, with given
  `opts` to pass to game. Players are initialized via
  `initial-players-fn`, taking as args [opts worlds]. Measurements are
  taken via `step-measure` and `game-measure`"
  [{:as opts :keys [batch-size] :or {batch-size 1}} nb-games seed initial-players-fn]
  (assert (s/valid? ::batch-size batch-size))
  (assert (or (< 1 batch-size) (= initial-m00-players initial-players-fn))
          "For a single player (batch size 1), the only valid
          initial-players-fn is inital-m00-players")
  (assert (zero? (mod nb-games batch-size))
          "Number of games must be a multiple of batch size")
  (let [world-seeds (seeded-seeds seed 0 nb-games)
        worlds
        (map #(aiw/world default-board-size %) world-seeds)
        new-opts
        (-> (assoc opts :seed seed)
            (update :step-measure-fn #(or % mzme/step-measure)))]
    (-> (reduce (u/with-logs #'run-and-measure-game-batch)
                (initial-players-fn new-opts worlds)
                (partition-all batch-size worlds))
        unwrap)))

(defn run-games
  ([opts nb-games seed initial-players-fn]
   (let [add-computation-mode
         (fn [opts computation-mode]
           (cond-> opts
             computation-mode
             (assoc-in [:ann-impl :computation-mode] computation-mode)))
         run-games-with-factory
         #(run-games-base (add-computation-mode opts %)
                          nb-games
                          seed
                          initial-players-fn)]
     (case (:computation-mode opts)
       (:cpu nil)
       (run-games-with-factory nil)
       ;; opencl & cuda libs are not in ns declaration, to avoid trying to load
       ;; them when they're not needed (and maybe not even installed)
       :gpu-opencl 
       (do
         (#'clojure.core/serialized-require (symbol "mzero.ai.train-opencl"))
         (apply (resolve (symbol "mzero.ai.train-opencl/run-opencl"))
                run-games-with-factory nil))
       
       :gpu-cuda
       (do 
         (#'clojure.core/serialized-require (symbol "mzero.ai.train-cuda"))
         (apply (resolve (symbol "mzero.ai.train-cuda/run-cuda"))
                run-games-with-factory nil)))))
  ([{:as opts :keys [batch-size]} nb-games seed]
   (let [initial-players-fn
         (if (and batch-size (< 1 batch-size))
           initial-shallow-players
           initial-m00-players)]
     (run-games opts nb-games seed initial-players-fn))))

(defmulti continue-games  
  "Continue games where left off with given player(s)."
  (fn [player-s _nb-games _seed]
    (cond (satisfies? aip/Player player-s) :single-player
          (satisfies? aip/Player (first player-s))  :multi-player)))

(defmethod continue-games :multi-player
  [players nb-games seed]
  (assert (zero? (mod nb-games (count players)))
          "Number of games must be a multiple of batch size")
  (let [nb-played-games (apply + (map #(count (:game-measurements %)) players))
        world-seeds (seeded-seeds seed nb-played-games nb-games)
        worlds
        (map #(aiw/world default-board-size %) world-seeds)]
    (-> (reduce (u/with-logs #'run-and-measure-game-batch)
                players
                (partition-all (count players) worlds))
        unwrap)))

(defmethod continue-games :single-player
  [player nb-games seed]
  (continue-games (vector player) nb-games seed))

(defn -main [& args]
  (apply run-games (map read-string args)))
