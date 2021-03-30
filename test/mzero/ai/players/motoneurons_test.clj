(ns mzero.ai.players.motoneurons-test
  (:require [mzero.ai.players.motoneurons :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest check-spec]]
            [mzero.utils.utils :as u]))

(check-spec `sut/next-direction)

(deftest next-direction-test
  (let [rng (java.util.Random. 30)
        random-10000-freqs
        (->> #(sut/next-direction rng [1.0 0.4 1.0 0.99])
             (repeatedly 1000)
             vec
             frequencies)
        perfect-average
        {:up 500 :down 500}]

    (is (->> [:up :down]
             (map #(u/almost= (% random-10000-freqs) (% perfect-average) 50))
             (every? true?)))))
