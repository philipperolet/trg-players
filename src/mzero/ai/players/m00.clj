(ns mzero.ai.players.m00
  "M0 player, early version. See arch-major/minor."
  (:require [mzero.ai.player :as aip]
            [mzero.ai.players.senses :as mzs]
            [mzero.ai.players.activation :as mza]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [uncomplicate.neanderthal
             [core :as nc]
             [native :as nn]
             [random :as rnd]]
            [mzero.game.events :as ge]
            [clojure.spec.alpha :as s]
            [uncomplicate.neanderthal.vect-math :as nvm]
            [clojure.data.generators :as g]))

(def dl-default-vision-depth 4)

(defn- direction-from-output
  [output-vector]
  (->> (nc/sum output-vector)
       (#(- % (int %))) ;; decimal part
       (* 100)
       int
       (#(mod % 4))
       (nth ge/directions)))

(defn- sparsify-weights
  "Nullify most of the weights so that patterns have a chance to match.

  A pattern's proba to match gets smaller as the number of non-nil
  weights increases."
  [layers]
  (let [rand-nonzeros ; random number of non-zero weights for a column
        #(max 2 (g/uniform 0 (* 0.25 (Math/sqrt (nc/dim %)))))
        rand-nonzero-vector
        #(nn/dv (g/shuffle (into (repeat %1 1.0) (repeat (- %2 %1) 0))))
        sparsify-column
        #(nvm/mul! %1 (rand-nonzero-vector %2 (nc/dim %1)))
        sparsify-layer
        (fn [{:as layer, :keys [::mza/weights]}]
          (doall (map sparsify-column
                      (nc/cols weights)
                      (repeatedly (nc/ncols weights) #(rand-nonzeros weights))))
          layer)]

    (vec (map sparsify-layer layers))))

(defn- youprint [layers]
  (doall (map #(do (println (-> % ::mza/patterns))
                   (println (-> % ::mza/weights) "||||||\n")) layers))
  layers)

(defrecord M00Player [layer-dims]
  aip/Player
  (init-player [player opts {{:keys [::gb/game-board]} ::gs/game-state}]
    (let [vision-depth (:vision-depth opts dl-default-vision-depth)
          input-size (mzs/senses-vector-size vision-depth)
          layer-dims (:layer-dims opts)
          thal-rng (if-let [seed (:seed opts)]
                     (rnd/rng-state nn/native-float seed)
                     (rnd/rng-state nn/native-float))
          seeded-sparsify-weights
          #(binding [g/*rnd* (:rng player)] (sparsify-weights %))]
      (mzs/vision-depth-fits-game? vision-depth game-board)
      (assert (s/valid? (s/+ ::mza/layer-dimension) layer-dims))
      (assoc player
             ;; neanderthal needs its own rng-state in addition to the
             ;; player's regular rng
             :thal-rng thal-rng 
             :layers (-> (mza/new-layers thal-rng (into [input-size] layer-dims))
                         ;; initializing weights and patterns so that
                         ;; movements vary (otherwise the same
                         ;; direction is always picked)
                         seeded-sparsify-weights
                         (update-in [0 ::mza/patterns] #(nc/scal! 0 %))
                         (update-in [2 ::mza/patterns] #(nc/scal! 0 %)))
             :senses-data (mzs/initial-senses-data vision-depth))))

  (update-player [player world]
    (let [player-forward-pass
          #(mza/forward-pass! (-> % :layers)
                              (-> % :senses-data ::mzs/senses))
          update-direction
          #(assoc % :next-movement (direction-from-output (player-forward-pass %)))]
      (-> player
          (update :senses-data mzs/update-senses-data world)
          update-direction))))
