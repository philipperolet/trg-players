(ns mzero.ai.players.m00
  "M0 player, early version. See arch-major/minor."
  (:require [mzero.ai.player :as aip]
            [mzero.ai.players.senses :as mzs]
            [mzero.ai.players.activation :as mza]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [uncomplicate.neanderthal
             [core :as nc]
             [native :refer [native-float]]
             [random :as rnd]]
            [mzero.game.events :as ge]
            [clojure.spec.alpha :as s]))

(def dl-default-vision-depth 4)

(defn- direction-from-output
  [output-vector]
  (->> (nc/sum output-vector)
       (#(- % (int %))) ;; decimal part
       (* 100)
       int
       (#(mod % 4))
       (nth ge/directions)))

(defrecord M00Player [layer-dims]
  aip/Player
  (init-player [player opts {{:keys [::gb/game-board]} ::gs/game-state}]
    (let [vision-depth (:vision-depth opts dl-default-vision-depth)
          input-size (mzs/senses-vector-size vision-depth)
          layer-dims (:layer-dims opts)
          rng (if-let [seed (:seed opts)]
                (rnd/rng-state native-float seed)
                (rnd/rng-state native-float))]
      (mzs/vision-depth-fits-game? vision-depth game-board)
      (assert (s/valid? (s/+ ::mza/layer-dimension) layer-dims))
      (assoc player
             :rng rng
             :layers (mza/new-layers rng (into [input-size] layer-dims))
             :senses-data (mzs/initial-senses-data vision-depth))))

  (update-player [player world]
    (let [player-forward-pass
          #(mza/forward-pass! (-> % :layers)
                              (-> % :senses-data ::mzs/senses-vector))
          update-direction
          #(assoc % :next-movement (direction-from-output (player-forward-pass %)))]
      (-> player
          (update :senses-data mzs/update-senses-data world)
          update-direction))))
