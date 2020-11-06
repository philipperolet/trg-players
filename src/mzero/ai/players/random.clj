(ns mzero.ai.players.random
  (:require [mzero.ai.player :refer [Player]]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [mzero.game.events :as ge]))

(defrecord RandomPlayer []
  Player
  (init-player [player _ _] player)
  (update-player [player _]
    (assoc player :next-movement (gen/generate (s/gen ::ge/direction)))))

