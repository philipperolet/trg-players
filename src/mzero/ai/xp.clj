(ns mzero.ai.xp
  "Namespace to experiment stuff peacefully.
  Not required by any other namespace"
  (:require [mzero.ai.main :as aim :refer [go gon n]]
            [mzero.ai.players.tree-exploration :as te]
            [mzero.ai.world :as aiw]
            [mzero.game.events :as ge]
            [mzero.game.generation :as gg]))

(defn get-worlds
  [board-size seed nb]
  (map aiw/get-initial-world-state
       (gg/generate-game-states nb board-size seed true)))

