(ns mzero.ai.players.base
  "Base for AI players implementation
  
  Options:

  -`:step-measure-fn` function run at the beginning each time a player
  is updated allowing to take measurements at every step of the game;

  - `:ann-impl` to spec the neural network that the player should use
  
  -`:layer-dims` for hidden layer sizes and `:weights-generation-fn`
  for initialization of ANN layers"
  (:require [clojure.spec.alpha :as s]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.network :as mzn]
            [mzero.ai.players.m0-modules.motoneurons :as mzm]
            [mzero.ai.ann.activations :as mza]
            [mzero.utils.utils :as u]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.ai.ann.initialization :as mzi]
            [mzero.game.state :as gs]
            [mzero.ai.world :as aiw]))

(s/def ::step-measure-fn fn?)
(s/def ::layer-dims (s/every ::mzn/dimension :min-count 1))
(s/def ::weights-generation-fn mzn/weights-generating-fn-spec)
(s/def ::ann-impl (s/or :impl ::mzann/ann :opts ::mzann/ann-opts))
(s/def ::player-opts
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
   :ann-impl-name "neanderthal-impl"})

(defn create-ann-impl-from-opts
  [{:as opts :keys [weights-generation-fn ann-impl layer-dims]}
   seed]
  (let [network-dims
        (cons (* mzs/input-vector-size mzs/short-term-memory-length) layer-dims)
        layers (initialize-layers network-dims weights-generation-fn seed)
        ann-impl-with-defaults (merge ann-default-opts ann-impl)]
    (-> (u/load-impl (-> ann-impl-with-defaults :ann-impl-name) "mzero.ai.ann")
        (mzann/initialize layers ann-impl-with-defaults))))

(def player-default-opts
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

(defn initialize-player [player opts {:as world, :keys [::gs/game-state]}]
  (assert (s/valid? ::player-opts opts) opts)
  (let [opts (merge player-default-opts opts)
          seed (. (:rng player) nextInt)
          ann-impl (initialize-ann-impl opts seed)          
          brain-tau (mzann/nb-layers ann-impl)
          senses (mzs/initialize-senses! brain-tau game-state)]
      (assoc player
             ::mzs/senses senses
             :ann-impl ann-impl
             ::step-measure-fn (:step-measure-fn opts))))

(defn record-measure
  [player {:as world :keys [::aiw/game-step]}]
  (cond->> player
      (not= 0 game-step) ;; nothing to measure when game starts
      ((::step-measure-fn player) world)))
