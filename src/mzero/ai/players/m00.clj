(ns mzero.ai.players.m00
  "M0 player, early version. See arch-major/minor."
  (:require [mzero.ai.player :as aip]
            [mzero.ai.players.senses :as mzs]
            [mzero.ai.players.activation :as mza]
            [mzero.ai.players.motoneurons :as mzm]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [uncomplicate.neanderthal
             [core :as nc]
             [native :as nn]
             [random :as rnd]]
            [clojure.spec.alpha :as s]
            [uncomplicate.neanderthal.vect-math :as nvm]
            [clojure.data.generators :as g]))

(defn- sparsify-weights
  "Nullify most of the weights so that patterns have a chance to match.

  A pattern's proba to match gets smaller as the number of non-nil
  weights increases."
  [layers]
  (let [rand-nonzeros ; random number of non-zero weights for a column
        #(max 2 (g/uniform 0 (* 0.1 (nc/mrows %))))
        rand-nonzero-vector
        #(nn/fv (g/shuffle (into (repeat %1 1.0) (repeat (- %2 %1) 0))))
        sparsify-column
        #(nvm/mul! %1 (rand-nonzero-vector %2 (nc/dim %1)))
        sparsify-layer
        (fn [{:as layer, :keys [::mza/weights]}]
          (doall (map sparsify-column
                      (nc/cols weights)
                      (repeatedly (nc/ncols weights) #(rand-nonzeros weights))))
          layer)]

    (vec (map sparsify-layer layers))))

(defn- create-thal-rng
  "Neanderthal needs its own rng-state in addition to the player's
  regular rng"
  [player-opts]
  (if-let [seed (:seed player-opts)]
    (rnd/rng-state nn/native-float seed)
    (rnd/rng-state nn/native-float)))

(defn- initialize-layers
  "Initialize weights and patterns so that movements vary (otherwise the
  same direction is always picked)"
  [rng thal-rng all-layer-dims]
  (binding [g/*rnd* rng]
    (-> (mza/new-layers thal-rng all-layer-dims)
        sparsify-weights
        (update-in [0 ::mza/patterns] #(nc/scal! 0 %))
        (update-in [(- (count all-layer-dims) 2) ::mza/patterns] #(nc/scal! 0 %)))))

(defrecord M00Player []
  aip/Player
  (init-player
    [player opts {:as world, :keys [::gs/game-state]}]
    (let [brain-tau (+ 2 (count (:layer-dims opts)))
          senses (mzs/initialize-senses! brain-tau game-state)
          input-size (count (::mzs/input-vector senses))
          thal-rng (create-thal-rng opts)
          all-layer-dims
          (conj (into [input-size] (:layer-dims opts)) mzm/motoneuron-number)]
      (assert (s/valid? (s/every ::mza/layer-dimension) all-layer-dims))
      (assoc player
             :thal-rng thal-rng
             :layers (initialize-layers (:rng player) thal-rng all-layer-dims)
             ::mzs/senses senses)))

  (update-player [player {:as world, :keys [::gs/game-state]}]
    (let [player-forward-pass
          #(mza/forward-pass! (-> % :layers) (-> % ::mzs/senses ::mzs/input-vector))

          update-senses
          #(mzs/update-senses % world player)
          
          make-move
          #(->> (player-forward-pass %)
                seq
                (mzm/next-direction (:rng player))
                (assoc % :next-movement))]
      (-> player
          (update ::mzs/senses update-senses)
          make-move))))
