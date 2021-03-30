(ns mzero.ai.players.m00
  "M0 player, early version. See arch-major/minor."
  (:require [mzero.ai.player :as aip]
            [mzero.ai.players.senses :as mzs]
            [mzero.ai.players.activation :as mza]
            [mzero.ai.players.network :as mzn]
            [mzero.ai.players.motoneurons :as mzm]
            [mzero.game.state :as gs]
            [uncomplicate.neanderthal
             [core :as nc]
             [native :as nn]
             [random :as rnd]]
            [clojure.spec.alpha :as s]
            [uncomplicate.neanderthal.vect-math :as nvm]
            [clojure.data.generators :as g]
            [mzero.utils.utils :as u]))

(s/fdef scale-float
  :args (s/cat :flt (-> (s/double-in 0 1)
                      (s/and float?))
               :low float?
               :hi float?))

(defn- scale-float [flt low hi] (+ low (* flt (- hi low))))

(def nwr "Negative weights ratio" 0.1)
(defn nonzero-weights-nb
  "Number of nonzero weights for a column of size `dim` given a range
  `flt` between 0 and 1"
  ([dim flt]
   (int (inc (* (Math/sqrt dim) (scale-float flt 0.3 1.0)))))
  ([dim] (nonzero-weights-nb dim (g/float))))

(defn- rand-nonzero-vector
  [dim]
  (let [normalized-randsign-weight ;; weight with normalized val, and random sign
        #(* (u/weighted-rand-nth [1 -1] [(- 1 nwr) nwr]) (/ %))
        nzw (nonzero-weights-nb dim)]
    (-> (into (repeatedly nzw #(normalized-randsign-weight nzw))
              (repeat (- dim nzw) 0))
        g/shuffle
        nn/fv)))

(defn- sparsify-weights
  "Nullify most of the weights so that patterns have a chance to match.

  A pattern's proba to match gets smaller as the number of non-nil
  weights increases."
  [layers]
  (let [sparsify-column
        (fn [col] (nvm/mul! col (rand-nonzero-vector (nc/dim col))))
        sparsify-layer
        (fn [{:as layer, :keys [::mzn/weights]}]
          (doall (map sparsify-column (nc/cols weights)))
          layer)]
    (vec (map sparsify-layer layers))))

(defn- create-ndt-rng
  "Neanderthal needs its own rng-state in addition to the player's
  regular rng"
  [player-opts]
  (if-let [seed (:seed player-opts)]
    (rnd/rng-state nn/native-float seed)
    (rnd/rng-state nn/native-float)))

(defn- initialize-layers
  "Initialize layers, with inner layers' weights and patterns
  sparsified, so that neurons are similar to what they might be when
  generation starts, and randomized so that movements vary (otherwise
  the same direction is always picked)"
  [rng ndt-rng layer-dims input-dim]
  (binding [g/*rnd* rng]
    (-> (mzn/new-layers ndt-rng (cons input-dim layer-dims))
        sparsify-weights
        ;; custom init of first layer to zero patterns
        (update-in [0 ::mzn/patterns] #(nc/scal! 0 %))
        (mzm/plug-motoneurons ndt-rng))))

(defrecord M00Player []
  aip/Player
  (init-player
    [player opts {:as world, :keys [::gs/game-state]}]
    {:pre [(s/valid? (s/every ::mzn/layer-dimension) (:layer-dims opts))]}
    (let [brain-tau (+ 2 (count (:layer-dims opts)))
          senses (mzs/initialize-senses! brain-tau game-state)
          input-dim (count (::mzs/input-vector senses))
          ndt-rng (create-ndt-rng opts)
          initial-layers
          (initialize-layers (:rng player) ndt-rng (:layer-dims opts) input-dim)]
      (assoc player
             :layers initial-layers
             ::mzs/senses senses)))

  (update-player [player {:as world, :keys [::gs/game-state]}]
    (let [player-forward-pass
          #(mza/forward-pass! (-> % :layers)
                              (-> % ::mzs/senses ::mzs/input-vector))

          make-move
          #(->> (player-forward-pass %)
                seq
                (mzm/next-direction (:rng player))
                (assoc % :next-movement))]
      (-> player
          (update ::mzs/senses #(mzs/update-senses % world player))
          make-move))))
