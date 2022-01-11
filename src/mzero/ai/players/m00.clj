(ns mzero.ai.players.m00
  "M0 player, early version. See arbre/version docs.

  Specific available options for M00:

  -`:step-measure-fn` function run at the beginning each time a player
  is updated allowing to take measurements at every step of the game;

  - `:ann-impl` to spec the neural network that the player should use
  
  -`:layer-dims` for hidden layer sizes and `:weights-generation-fn`
  for initialization of ANN layers"
  (:require [clojure.data.generators :as g]
            [clojure.spec.alpha :as s]
            [mzero.ai.ann.activations :as mza]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.initialization :as mzi]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.ann.network :as mzn]
            [mzero.ai.player :as aip]
            [mzero.ai.players.m0-modules.m00-reinforcement :as m00r]
            [mzero.ai.players.m0-modules.motoneurons :as mzm]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.ai.world :as aiw]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.utils.utils :as u]
            [uncomplicate.commons.core :refer [release Releaseable]]))

(s/def ::step-measure-fn fn?)
(s/def ::layer-dims (s/every ::mzn/dimension :min-count 1))
(s/def ::weights-generation-fn mzn/weights-generating-fn-spec)
(s/def ::ann-impl (s/or :impl ::mzann/ann :opts ::mzann/ann-opts))
(s/def ::m00-opts
  (s/keys :req-un [::mzann/layer-dims]
          :opt-un [::step-measure-fn
                   ::weight-generation-fn
                   ::ann-impl]))

(defn initialize-layers
  "Initialize layers with given `dimensions` (the first dimension is the
  input dimension), using weight initialization function `weights-fn`

  Layer and input dimensions are increased of 1 to allow for b in
  w.x+b perceptron formula. Later, propagation ensures it is always
  set to 1."
  [dimensions weights-fn seed]
  (let [dimensions (map inc dimensions)]
    (-> (mzn/new-layers dimensions weights-fn (inc seed))
        (mzm/plug-motoneurons (dec seed)))))

(def ann-default-opts
  {:act-fns mza/trelu
   :label-distribution-fn mzld/ansp
   :ann-impl-name "neanderthal-impl"})

(defn- create-ann-impl-from-opts
  [{:as opts :keys [weights-generation-fn ann-impl layer-dims]}
   seed]
  (let [network-dims
        (cons (* mzs/input-vector-size mzs/short-term-memory-length) layer-dims)
        layers (initialize-layers network-dims weights-generation-fn seed)
        ann-impl-with-defaults (merge ann-default-opts ann-impl)]
    (-> (u/load-impl (-> ann-impl-with-defaults :ann-impl-name) "mzero.ai.ann")
        (mzann/initialize layers ann-impl-with-defaults))))

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

(def m00-default-opts
  {:step-measure-fn (fn [_ p] p)})

(def layer-gen-default-opts {:weights-generation-fn mzi/angle-sparse-weights})

(defn- initialize-ann-impl
  "If ann-impl is already an ann, nothing to do; otherwise if ann
  options were given, create and init an new ann-impl"
  [{:as opts :keys [ann-impl]} seed]
  (cond
    (s/valid? ::mzann/ann ann-impl) ann-impl
    
    (s/valid? ::mzann/ann-opts ann-impl)
    (create-ann-impl-from-opts (merge layer-gen-default-opts opts) seed)
    
    :else
    (throw (ex-info "Invalid ann spec: " ann-impl))))

(defn- record-measure
  [player {:as world :keys [::aiw/game-step]}]
  (cond->> player
      (not= 0 game-step) ;; nothing to measure when game starts
      ((::step-measure-fn player) world)))

(defn initialize-player [player opts {:as world, :keys [::gs/game-state]}]
  (assert (s/valid? ::m00-opts opts) opts)
  (let [opts (merge m00-default-opts opts)
          seed (. (:rng player) nextInt)
          ann-impl (initialize-ann-impl opts seed)          
          brain-tau (mzann/nb-layers ann-impl)
          senses (mzs/initialize-senses! brain-tau game-state)]
      (assoc player
             ::mzs/senses senses
             :ann-impl ann-impl
             ::step-measure-fn (:step-measure-fn opts))))

(defrecord M00Player []
  aip/Player
  (init-player [player opts world] (initialize-player player opts world))

  (update-player [player {:as world, :keys [::gs/game-state ::aiw/game-step]}]
    (binding [g/*rnd* (-> player :rng)]
      (-> player
          (record-measure world)
          (update ::mzs/senses mzs/update-senses world player)
          (m00r/backward-pass game-state game-step)
          select-action)))
  
  Releaseable
  (release [{:keys [ann-impl]}]
    (release ann-impl)))
