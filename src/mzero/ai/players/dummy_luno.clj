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
             [core :refer [mm entry ncols matrix?]]
             [native :refer [dge native-float]]
             [random :as rnd]
             [vect-math :as vm]]
            [clojure.spec.alpha :as s]))

(def vision-depth 4)
(def default-hidden-layer-size 6)

(defn- get-int-from-decimals
  " Gets an int in [0,100[ by using 2nd & 3rd numbers past
  decimal point of `value`"
  [value]
  (let [get-decimal-part #(- % (int %))]
    (-> (* value 10)
        get-decimal-part
        (* 100)
        int)))

(defn- board-subset-vector
  "Turn the board subset visible by the player from keyword
  matrix to a real-valued vector.

  Each type of elt on the board has a corresponding float value
  between 0.0 - 1.0, as described below"
  [board-subset]
  (->> board-subset
       (reduce into [])
       (map {:wall 1.0 :empty 0.0 :fruit 0.5})
       vec))

(defn- real-valued-senses
  "Compute real-valued vector of player senses"
  [player-senses]
  (board-subset-vector (::aip/board-subset player-senses)))

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

(defn- validate-edge-length
  [world edge-length]
  (let [board-size (-> world ::gs/game-state ::gb/game-board count)]
    (assert (< edge-length board-size))))

(defrecord DummyLunoPlayer [hidden-layer-size]
  aip/Player
  (init-player [player opts world]
    (let [edge-length (aip/subset-size vision-depth)
          input-size (Math/pow edge-length 2)
          hl-size (:hidden-layer-size opts default-hidden-layer-size)
          rng (if-let [seed (:seed opts)]
                (rnd/rng-state native-float seed)
                (rnd/rng-state native-float))]
      (validate-edge-length world edge-length)
      (assoc player
             :rng rng
             :hidden-layer (create-hidden-layer input-size hl-size rng))))
  
  (update-player [player world]
    (let [player-senses (aip/get-player-senses world vision-depth)
          real-valued-senses (real-valued-senses player-senses)
          input-vector (dge 1 (count real-valued-senses) real-valued-senses)]
      (assoc player :next-movement (new-direction player input-vector)))))
