(ns mzero.ai.players.motoneurons-test
  (:require [mzero.ai.players.motoneurons :as sut]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest check-spec]]
            [mzero.utils.utils :as u]
            [mzero.ai.main :as aim]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [mzero.game.generation :as gg]
            [mzero.ai.world :as aiw]
            [uncomplicate.neanderthal.native :as nn]
            [mzero.ai.players.network :as mzn]
            [uncomplicate.neanderthal.core :as nc]
            [mzero.ai.players.senses :as mzs]
            [uncomplicate.neanderthal.random :as rnd]
            [mzero.ai.player :as aip]
            [mzero.game.events :as ge]))

(check-spec `sut/next-direction)
(def seed 30)

(deftest next-direction-test
  (let [rng (java.util.Random. seed)
        random-10000-freqs
        (->> #(sut/next-direction rng [1.0 0.4 1.0 0.99])
             (repeatedly 1000)
             vec
             frequencies)
        perfect-average
        {:up 500 :down 500}]

    (is (->> [:up :down]
             (map #(u/almost= (% random-10000-freqs) (% perfect-average) 50))
             (every? true?)))
    (testing "Should return nil if no value is at one"
      (is (nil? (sut/next-direction rng [0.1 0.99 0.2 0.3]))))))

#_(deftest random-move-reflex-setup
  (let [layers
        (sut/setup-random-move-reflex
         (mzn/new-layers (cons 83 (repeat 6 64))
                         #(rnd/rand-uniform! (nn/fge %1 %2))))]
    (is (every? #(= 200.0 %)
                (->> layers last ::mzn/weights nc/cols (map last) (take 4))))))

;; Will become a randomness-reflex test
#_(deftest ^:integration m00-randomness
  :unstrumented
  (testing "Checks that over 1000 steps, 50 moves at least are made,
  and over 10 in each direction (where the perfect split would be
  12.5/12.5/12.5/12.5)"
    (let [test-world (aiw/world 25 seed)
          steps 1000
          m00-opts {:seed seed :layer-dims (repeat 8 256)}
          game-opts
          (aim/parse-run-args "-v WARNING -n %d -t m00 -o'%s'" steps m00-opts)
          request-and-store
          (let [req-mov aip/request-movement]
            (fn [player-st world-st]
              (swap! player-st update :move-list
                     conj [(-> @world-st ::aiw/game-step)
                           (-> @player-st :next-movement)])
              (req-mov player-st world-st)))]
      (with-redefs [aip/request-movement request-and-store]
        (let [pl (-> (aim/run game-opts test-world) :player)]
          (is (every? #(> % 10) (map (frequencies (->> pl :move-list (map second))) ge/directions)))
          (is (< 50 (count (->> pl :move-list (map second) (remove nil?))))))))))
