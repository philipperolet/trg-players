(ns mzero.ai.players.motoneurons-test
  (:require [mzero.ai.players.motoneurons :as sut]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest check-spec]]
            [clojure.data.generators :as g]
            [mzero.utils.utils :as u]
            [mzero.game.events :as ge]))

(check-spec `sut/next-direction)

(deftest next-direction-test
  (let [random-10000-freqs
        (frequencies
         (binding [g/*rnd* (java.util.Random. 30)]
           (vec (repeatedly 10000 #(sut/next-direction [0.4 0.1 0.1 0.2])))))

        perfect-average
        {:up 5000 :right 1250 :down 1250 :left 2500}]

    (is (->> ge/directions
             (map #(u/almost= (% random-10000-freqs) (% perfect-average) 100))
             (every? true?)))))
