(ns mzero.ai.players.dummy-luno
  "Player that gets the input senses, turns them into a real-valued
  vector, and uses a dummy 'ANN' on which it makes a 'forward pass',
  using the real-valued result to compute a random movement.

  The network comprises a hidden layer and an output vector.
  The number of units in the hidden layer can be set via
  the `:hidden-layer-size` option"
  (:require [mzero.ai.player :as aip]
            [mzero.game.events :as ge]
            [clojure.data.generators :as g]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]))

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

(defn- get-real-valued-senses
  "Turns the board subset visible by the player (senses) from keyword
  matrix to real-valued vector"
  [world vision-depth]
  (->> (aip/get-player-senses world vision-depth)
       ::aip/board-subset
       (reduce into [])
       (map {:wall 1.0 :empty 0.0 :fruit 0.5})
       vec))

(defn- create-hidden-layer
  "`rng`: random number generator"
  [input-size layer-size rng]
  (binding [g/*rnd* rng]
    (->> #(vec (repeatedly input-size g/float)) ;; single unit
         (repeatedly layer-size)
         vec)))

(defn- dot-prod
  [coll1 coll2]
  (assert (= (count coll1) (count coll2)))
  (reduce + (map * coll1 coll2)))

(defn- forward-pass
  [input-vector hidden-layer output-vector]
  (->> hidden-layer
       (map #(dot-prod input-vector %))
       (dot-prod output-vector)))

(defrecord DummyLunoPlayer [hidden-layer-size]
  aip/Player
  (init-player [player opts world]
    (let [edge-length (aip/subset-size vision-depth)
          board-size (-> world ::gs/game-state ::gb/game-board count)
          input-size (Math/pow edge-length 2)
          hl-size (:hidden-layer-size opts default-hidden-layer-size)
          hidden-layer (create-hidden-layer input-size hl-size (:rng player))]
      
      (assert (< edge-length board-size))
      (assoc player :hidden-layer hidden-layer)))
  
  (update-player [player world]
    (let [input-vector
          (get-real-valued-senses world vision-depth)

          output-vector
          (binding [g/*rnd* (:rng player)]
            (vec (repeatedly (count (-> player :hidden-layer)) g/float)))]

      ;; next move is selected by getting the 2nd/3rd decimals of
      ;; forward pass to get an int in [0, 100[, then the remainder
      ;; of the division by 4 is the index of the selected direction
      (->> (forward-pass input-vector (-> player :hidden-layer) output-vector)
           get-int-from-decimals
           (#(mod % 4))
           (nth ge/directions)
           (assoc player :next-movement)))))
