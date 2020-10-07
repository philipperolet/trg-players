(ns claby.ai.players.random
  (:require [claby.ai.player :refer [Player]]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [claby.game.events :as ge]))

(defrecord RandomPlayer []
  Player
  (init-player [player _] player)
  (update-player [player _]
    (assoc player :next-movement (gen/generate (s/gen ::ge/direction)))))

