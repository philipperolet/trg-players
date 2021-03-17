(ns mzero.ai.players.motoneurons-test
  (:require [mzero.ai.players.motoneurons :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest check-spec]]
            [mzero.utils.utils :as u]
            [mzero.game.events :as ge]))

(check-spec `sut/next-direction)

(deftest next-direction-test
  (let [rng (java.util.Random. 30)
        random-10000-freqs
        (->> #(sut/next-direction rng [0.4 0.1 0.1 0.2])
             (repeatedly 10000)
             vec
             frequencies)
        perfect-average
        {:up 5000 :right 1250 :down 1250 :left 2500}]

    (is (->> ge/directions
             (map #(u/almost= (% random-10000-freqs) (% perfect-average) 100))
             (every? true?)))))
