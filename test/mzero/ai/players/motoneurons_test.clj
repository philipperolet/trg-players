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

(deftest setup-fruit-arcreflex-in-direction
  (let [layer {::mzn/weights (nn/fge 200 sut/motoneuron-number)
               ::mzn/patterns (nn/fge 200 sut/motoneuron-number)}
        w-col (nth (nc/cols (::mzn/weights layer)) 1)
        p-col (nth (nc/cols (::mzn/patterns layer)) 1)]
    (#'sut/setup-fruit-arcreflex-in-direction! layer :right)
    (is (every? #(= (nc/entry p-col %) 0.5) [31 39 41 49]))
    (is (every? #(= (nc/entry w-col %) -500.0) [31 39 49]))
    (is (= (nc/entry w-col 41) 1000.0))))

(deftest arcreflexes-test
  :unstrumented
  (testing "next-fruit arcreflex: should eat all the fruits without
  going anywhere else on the board, since there is always a fruit next
  to the player until the board is empty.")
  (let [test-board
        (-> (gb/empty-board 20) ;; path below sows fruits up to [0 3]
            (gg/sow-path :fruit [0 0] [:down :down :right :right :right :up :up]))
        test-world
        (-> (aiw/new-world (gs/init-game-state test-board 0))
            (assoc-in [::gs/game-state ::gs/player-position] [0 0]))
        player-opts {:seed seed :layer-dims (repeat 8 1024)}
        game-opts
        (aim/parse-run-args "-v WARNING -t m00 -o '%s' -n 1" player-opts)
        stays-in-path
        (fn [g] (some #{(-> g :world ::gs/game-state ::gs/player-position)}
                      [[1 0] [2 0] [2 1] [2 2] [2 3] [1 3]]))
        run-step #(aim/run game-opts (:world %) (:player %))
        game-run ;; WARNING : use of iterate for a fn with side-effects
        (->> (iterate run-step (aim/run game-opts test-world))
             (remove stays-in-path)
             first)]
    (is (= [0 3] (-> game-run :world ::gs/game-state ::gs/player-position)))
    (is (gb/empty-board? (-> game-run :world ::gs/game-state ::gb/game-board)))
    (testing "motoinhibition arcreflex: it should take at least 7 *
    motoception-persistence steps to eat all fruits, since motoception
    blocks movements for a while"
      (let [brain-tau
            (-> game-run :player ::mzs/senses ::mzs/params ::mzs/brain-tau)]
        (is (> (-> game-run :world ::aiw/game-step)
               (* 7 (mzs/motoception-persistence brain-tau))))))))

(deftest random-move-reflex-setup
  (let [layers
        (sut/setup-random-move-reflex
         (mzn/new-layers (cons 83 (repeat 6 64))
                         #(rnd/rand-uniform! (nn/fge %1 %2))
                         #(rnd/rand-uniform! (nn/fge %1 %2))))]
    (is (every? #(= 200.0 %)
                (->> layers last ::mzn/weights nc/cols (map last) (take 4))))
    (is (every? #(= 1.0 %)
                (->> layers (map ::mzn/patterns) rest butlast (map last) (map last))))))

;; Will become a randomness-reflex test
(deftest ^:integration m00-randomness
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
