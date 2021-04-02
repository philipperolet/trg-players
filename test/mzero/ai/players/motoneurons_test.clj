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
            [uncomplicate.neanderthal.core :as nc]))

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

(deftest setup-fruit-arcreflex-in-direction
  (let [layer {::mzn/weights (nn/fge 200 sut/motoneuron-number)
               ::mzn/patterns (nn/fge 200 sut/motoneuron-number)}
        w-col (nth (nc/cols (::mzn/weights layer)) 1)
        p-col (nth (nc/cols (::mzn/patterns layer)) 1)]
    (#'sut/setup-fruit-arcreflex-in-direction! layer :right)
    (is (every? #(= (nc/entry p-col %) 0.5) [31 39 41 49]))
    (is (every? #(= (nc/entry w-col %) -500.0) [31 39 49]))
    (is (= (nc/entry w-col 41) 1000.0))))

(deftest next-fruit-arcreflex-test
  :unstrumented
  (let [test-board
        (-> (gb/empty-board 20)
            (gg/sow-path :fruit [0 0] [:down :down :left :left :left :up :up]))
        test-world
        (-> (aiw/new-world (gs/init-game-state test-board 0))
            (assoc-in [::gs/game-state ::gs/player-position] [0 0]))
        player-opts {:seed seed :layer-dims (repeat 8 1024)}
        game-opts
        (aim/parse-run-args "-v WARNING -t m00 -o '%s' -n 10" player-opts)
        game-run
        (aim/run game-opts test-world)]
    (is (gb/empty-board? (-> game-run :world ::gs/game-state ::gb/game-board)))))
