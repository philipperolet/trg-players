(ns mzero.ai.players.dummy-luno
  "Dummy player that gets the input senses, turns them into a
  real-valued vector, dot-prods them with a random vector and computes
  a movement according to it."
  (:require [mzero.ai.player :as aip]
            [mzero.game.events :as ge]
            [clojure.data.generators :as g]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]))

(def vision-depth 4)

(defn- get-int-from-decimals
  " Gets an int in [0,100[ by using 2nd & 3rd numbers past
  decimal point of `value`"
  [value]
  (let [get-decimal-part #(- % (int %))]

    (-> (* value 10)
        get-decimal-part
        (* 100)
        int)))

(defrecord DummyLunoPlayer []
  aip/Player
  (init-player [player _ world]
    (let [edge-length (aip/subset-size vision-depth)]
      (assert (< edge-length (-> world ::gs/game-state ::gb/game-board count)))
      (assoc player :vector-size (Math/pow edge-length 2))))
  
  (update-player [player world]
    (let [senses-data
          (aip/get-player-senses world vision-depth)

          real-valued-senses
          (->> senses-data ::aip/board-subset
               (reduce into [])
               (map {:wall 1.0 :empty 0.0 :fruit 0.5})
               vec)

          random-vector
          (vec (repeatedly (-> player :vector-size) g/float))

          dot-producted-value
          (reduce + (map * real-valued-senses random-vector))

          ;; next move is selected by getting the 2nd/3rd decimals of
          ;; dot product to get an int in [0, 100[, then the remainder
          ;; of the division by 4 is the index of the selected direction
          direction-from-int 
          (nth ge/directions (mod (get-int-from-decimals dot-producted-value) 4))]
      
      (assoc player :next-movement direction-from-int))))
