(ns mzero.ai.players.dummy-luno
  "Player that gets the input senses, turns them into a real-valued
  vector, and uses a dummy 'ANN' on which it makes a 'forward pass',
  using the real-valued result to compute a random movement.

  The network comprises a hidden layer and an output vector.
  The number of units in the hidden layer can be set via
  the `:hidden-layer-size` option

  This implementation relies on the Neanderthal lib."
  (:require [mzero.ai.player :as aip]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [uncomplicate.neanderthal
             [core :refer [mm entry ncols]]
             [native :refer [dge native-float]]
             [random :as rnd]]
            [mzero.ai.players.senses :as mzs]))

(defn- get-int-from-decimals
  " Gets an int in [0,100[ by using 2nd & 3rd numbers past
  decimal point of `value`"
  [value]
  (let [get-decimal-part #(- % (int %))]
    (-> (* value 10)
        get-decimal-part
        (* 100)
        int)))

(defn- create-hidden-layer
  "`rng`: random number generator"
  [input-size layer-size rng]
  (rnd/rand-uniform! rng (dge input-size layer-size)))

(defn- forward-pass
  [input-vector hidden-layer output-vector]
  (-> input-vector
      (mm hidden-layer)
      (mm output-vector)
      (entry 0 0)))

(defn- direction-from-real
  "Computes a move by getting the 2nd/3rd decimals of `real` to get an
  int in [0, 100[, then the remainder of the division by 4 is the
  index of the selected direction"
  [real]
  (->> real
       get-int-from-decimals
       (#(mod % 4))
       (nth ge/directions)))

(defn- new-direction
  [{:as player, :keys [hidden-layer rng]} input-vector]
  (let [output-vector (rnd/rand-uniform! rng (dge (ncols hidden-layer) 1))]
    (direction-from-real (forward-pass input-vector hidden-layer output-vector))))

(def dl-default-hidden-layer-size 6)

(defrecord DummyLunoPlayer [hidden-layer-size]
  aip/Player
  (init-player [player opts {:keys [::gs/game-state]}]
    (let [brain-tau 5 ;; brain-tau not relevant for dummy-luno, any value is fine
          senses (mzs/initialize-senses! brain-tau game-state (java.util.Random. (:seed opts))) 
          hl-size (:hidden-layer-size opts dl-default-hidden-layer-size)
          rng (if-let [seed (:seed opts)]
                (rnd/rng-state native-float seed)
                (rnd/rng-state native-float))]
      (assoc player
             :rng rng
             :hidden-layer (create-hidden-layer mzs/input-vector-size hl-size rng)
             :senses senses)))
  
  (update-player [player world]
    (let [input-vector #(dge 1 (count %) %)
          update-movement-from-input-vector
          (fn [player]
            (->> (get-in player [:senses ::mzs/input-vector])
                 input-vector
                 (new-direction player)
                 (assoc player :next-movement)))]
      
      (-> player
          (update :senses mzs/update-senses world player)
          update-movement-from-input-vector))))
