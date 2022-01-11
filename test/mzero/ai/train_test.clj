(ns mzero.ai.train-test
  (:require [clojure.test :refer [is testing]]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.ai.train :as sut]
            [mzero.game.state :as gs]
            [mzero.utils.testing :refer [deftest]]
            [mzero.ai.measure :as mzme]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.world :as aiw]
            [clojure.pprint :refer [pprint]]
            [mzero.ai.players.m00 :as m00]
            [mzero.utils.utils :as u]
            [uncomplicate.neanderthal.native :as nn]
            [mzero.ai.ann.initialization :as mzi]
            [uncomplicate.neanderthal.core :as nc]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.ann.common :as mzc]
            [mzero.utils.xp :as xp]
            [mzero.game.board :as gb]
            [mzero.ai.players.base :as mzb]))

(defn- mock-step-measure [_ player]
  (update-in player
             [:step-measurements :moves]
             #(cons (:next-movement player) %)))

(defn- mock-game-measure [world player]
  (is (= (dec sut/nb-steps-per-game) ;; no measurement at step 0
         (-> player :step-measurements :moves count)))
  {:ups (->> player :step-measurements :moves (filter #{:up}) count)
   :score (-> world ::gs/game-state ::gs/score)})

(deftest run-games-test
  :unstrumented
  (with-redefs [mzme/step-measure mock-step-measure
                mzme/game-measure mock-game-measure
                sut/nb-steps-per-game 2000]
    (testing "Run for 3 games, correctly record step measurements and
  game measurements. Game measurements are the nb of :up
  moves and the score."
      (let [{:keys [::mzs/senses game-measurements]}
            (sut/run-games {:layer-dims [64 64]} 3 42)]
        (is (= (count game-measurements) 3))
        ;; check last measured score correct
        (is (= (-> senses ::mzs/data ::gs/game-state ::gs/score)
               (-> game-measurements last :score)))
        ;; number of ups should be around a bit less thant 1/4 total
        ;; steps (because of nil moves), around 400 (growing since
        ;; less and less nil fast)
        (is (every? #(< 300 % 600) (->> game-measurements (map :ups))))))
    (testing "Run for 6 games with 3 players, correctly record step measurements and
  game measurements. Game measurements are the nb of :up
  moves and the score."
      (let [run-opts {:layer-dims [64 64] :batch-size 3}
            players (sut/run-games run-opts 6 42 sut/initial-m00-players)
            game-measurements (flatten (map :game-measurements players))]
        (is (= (count game-measurements) 6))
        ;; check last measured score correct
        (is (= (-> players last ::mzs/senses ::mzs/data ::gs/game-state ::gs/score)
               (-> game-measurements last :score)))
        ;; number of ups should be around a bit less thant 1/4 total
        ;; steps (because of nil moves), around 400 (growing since
        ;; less and less nil fast)
        (is (every? #(< 300 % 600) (->> game-measurements (map :ups))))))))

(deftest continue-games-test
  :unstrumented
  (with-redefs [mzme/step-measure mock-step-measure
                mzme/game-measure mock-game-measure]
    (testing "Single player case"
      (let [{:keys [game-measurements]}
            (sut/run-games {:layer-dims [64 64]} 3 42)
            measurements-rungame
            (map #(dissoc % :moves-per-sec) game-measurements)
            measurements-rungame-and-continue
            (-> (sut/run-games {:layer-dims [64 64]} 1 42)
                (sut/continue-games 2 42)
                :game-measurements)]
        (is (= measurements-rungame measurements-rungame-and-continue))))
    (testing "Multiple players case"
      (let [run-opts {:layer-dims [64 64] :batch-size 2}
            game-measurements
            (->> (sut/run-games run-opts 4 42 sut/initial-m00-players)
                 (map :game-measurements)
                 flatten)
            measurements-rungame
            (map #(dissoc % :moves-per-sec) game-measurements)
            measurements-rungame-and-continue
            (-> (sut/run-games run-opts 2 42 sut/initial-m00-players)
                (sut/continue-games 2 42)
                (#(map :game-measurements %))
                flatten
                (#(map (fn [m] (dissoc m :moves-per-sec)) %)))]
        (is (= measurements-rungame measurements-rungame-and-continue))))))

(defn- store-bp-requests
  [{:as player :keys [ann-impl]}]
  (if-let [reqs (:bp-requests ann-impl)]
    (-> (update player :bp-requests #(or % []))
        (update :bp-requests conj reqs)
        (update :ann-impl dissoc :bp-requests))
    player))

(deftest players-with-shallow-mpanns
  :unstrumented
  (testing "Games run with multiple shallow mpanns have the same
  output as if they were run sequentially with one player. Only true
  without backprop, so backprop is deactivated BUT backprop requests
  stored, so we check they are exactly the same

  Consequently, the player's rng needs to be reset to the same value
  at start of each game otherwise executions of single player and
  multiplayer will differ

  IMPORTANT NOTE: there seems to be small differences in measurements
  between the runs, that may be due to float computation precision,
  notably via label distr. functions that use `exp` (ansp,
  softmax). Non-deterministic behaviour of floating-point ops is
  evidenced by the test `float-precision-non-deterministic`.

  Therefore, it is not equality but almost equality that has been
  sought, and a few errors are tolerated. However, it is not
  guaranteed that errors only come from there."
    (with-redefs [sut/nb-steps-per-game 1000
                  mzann/backward-pass!
                  (fn [ann-impl target-distr-tensor]
                    (-> (update ann-impl :bp-requests #(or % []))
                        (update :bp-requests conj target-distr-tensor)))
                  mzb/record-measure
                  (let [reset-fn (var-get #'mzb/record-measure)]
                    (fn [p w]
                      (if (zero? (-> w ::aiw/game-step))
                        (-> p
                            store-bp-requests
                            (assoc :rng (java.util.Random. 3)))
                        (reset-fn p w))))]
      (let [run-opts {:layer-dims [64 64]}
            single-player-run (sut/run-games run-opts 9 42)
            _3players-run (sut/run-games (assoc run-opts :batch-size 3) 9 42)
            measurements-single-player (:game-measurements single-player-run)
            bp-requests-single-player
            (flatten (:bp-requests (store-bp-requests single-player-run)))
            measurements-3players
            (apply interleave (map :game-measurements _3players-run))
            bp-requests-3players
            (->> (map store-bp-requests _3players-run)
                 (map :bp-requests)
                 (apply interleave)
                 flatten)]
        ;; small differences on wall-move-ratio ignored (diff of < 1%
        ;; on 2 out of nine, equality for others ATTOW) see fn doc
        (is (= (map #(dissoc % :wall-move-ratio) measurements-single-player)
               (map #(dissoc % :wall-move-ratio) measurements-3players)))
        ;; exact equality not required, see fn doc
        ;; instead, almost equality, almost all the time aka 99.9%, is sought
        (let [nb-elts (count bp-requests-single-player)
              nb-errors
              (->> (map u/almost= bp-requests-single-player bp-requests-3players)
                   (filter false?)
                   count)]
          (is (< (/ nb-errors nb-elts) 0.001)))))))

(deftest no-halt-when-game-over
  :unstrumented
  (testing "When one of the player finishes a game, the others should
  not hang and finish theirs. ATTOW, this is done by restarting a new
  game for the player"
    (let [run-opts {:layer-dims [64 64] :batch-size 3}
          add-fruit ;; add fruit 2 rows below
          #(-> (::gs/player-position %)
               (update 0 + 2)
               ((partial cons ::gb/game-board))
               ((partial assoc-in %) :fruit))
          simple-world
          (-> (gb/empty-board sut/default-board-size)
              (gs/init-game-state 0)
              add-fruit
              aiw/new-world)
          change-world-fn
          (fn [run-fn players worlds]
            ;; change a world so that the game is over very
            ;; fast, one fruit close to the player
            (->> worlds
                 rest
                 (cons simple-world)
                 (run-fn players)))]
      (with-redefs [sut/run-and-measure-game-batch
                    (partial change-world-fn
                             (var-get #'sut/run-and-measure-game-batch))]
        (is (= 3 (count (sut/run-games run-opts 3 42))))))))

(deftest ^:integration ^:skip multiplayer-learns-roughly-equally-to-single-player
  (testing "This test illustrates that batch learning using agents
  running in parallel performs at least as well as a sequential
  version. It is *illustrative* and is not intended to be run
  frequently, thus is skipped")
  :unstrumented
  ;; perf is the average fem ratio for 512 runs with [512 512] and
  ;; default opts ATTOW
  (let [reference-perf ;; perf with single player
        0.8726299135014415
        batch-of-8-perf
        0.8043690323829651]
    ;; 
    ;; should be around 0.9
    (is (u/almost= reference-perf batch-of-8-perf 0.07)))
  #_(let [run-opts {:ann-impl {:layer-dims [512 512]}}
        single-player-run (sut/run-games run-opts 512 42)
        multiplayers-run (sut/run-games (assoc run-opts :batch-size 4) 512 42)
        average-last-32
        (fn [measurements]
          (->> (take-last 32 measurements)
               (merge-with conj)
               (u/map-map xp/mean)))
        averages-single-player
        (average-last-32 (:game-measurements single-player-run))
        averages-3players
        (-> (apply interleave (map :game-measurements multiplayers-run))
            average-last-32)]
    (is (mzc/almost= (:score averages-3players)
                     (:score averages-single-player)
                     "1/32"))
    (is (mzc/almost= (:fruit-move-ratio averages-3players)
                     (:fruit-move-ratio averages-single-player)
                     "1/32"))))

(deftest ^:skip float-precision-non-deterministic
  :unstrumented
  (testing "Shows that same computations can lead to different results
  with some float ops. Skipped since the test is just illustrative,
  not codebase-dependant"
    #_(let [mamlot
          (nn/fge (mzi/random-weights 125 125 25))
          mamlot2
          (nn/fge (mzi/random-weights 125 125 26))
          cols-of-mamlot
          (-> #(nc/submatrix mamlot2 0 % 125 1)
              (map (range 125)))
          r1 (->> (flatten (seq (nc/mm mamlot mamlot2)))
                  (partition 5)
                  (mapv mzld/ansp)
                  flatten)
          r2 (-> #(->> (nc/mm mamlot %) seq flatten (partition 5) (map mzld/ansp))
                 (mapv cols-of-mamlot)
                 flatten)]
      (is (zero? (count (filter #(not= (first %) (second %)) (map vector r1 r2))))))))
