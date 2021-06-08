(ns mzero.ai.players.motoneurons-test
  (:require [clojure.test :refer [is testing are]]
            [mzero.ai.players.motoneurons :as sut]
            [mzero.utils.testing :refer [check-spec deftest]]
            [mzero.ai.world :as aiw]
            [mzero.ai.main :as aim]
            [mzero.ai.player :as aip]
            [mzero.ai.players.senses :as mzs]
            [mzero.game.events :as ge]
            [mzero.ai.players.activation :as mza]
            [mzero.ai.players.m00 :as m00]))

(check-spec `sut/next-direction)
(def seed 30)

(deftest next-direction-test
  (doseq [[motos dir] [[[0 0 0 0] nil]
                       [[-1 0 0.5 0] :down]
                       [[0.15 0.1 0.14 0.11] nil]
                       [[5.0 5.0 0 0] nil]
                       [[5.0 0.1 0.1 -6.0] :up]]]
    (is (= (sut/next-direction (vec (map double motos))) dir))))

(deftest random-move-reflex-works
  (let [layers
        (#'m00/initialize-layers (repeat 3 128)
                                 mzs/input-vector-size
                                 #(nn/fge %1 %2))
        input-vector-from
        (fn [m a1 a2]
          (-> (repeat (- mzs/input-vector-size 4) 0.0)
              vec
              (conj (float m) 0 (float a1) (float a2))))
        run-fp-and-get-dir
        (fn [moto a1 a2]
          (-> layers
              (mza/sequential-forward-pass! (input-vector-from moto a1 a2) true)
              seq
              sut/next-direction))]
    (are [moto a1 a2 chosen-direction]
        (= (run-fp-and-get-dir moto a1 a2) chosen-direction)
      1 1 1 nil
      1 0 1 nil
      1 1 0 nil
      1 0 0 nil
      1 0.5 0.5 nil
      1 0.7 0.2 nil
      0 0 0 :up
      0 0.2 0.4 :up
      0 1 0 :right
      0 0.7 0.2 :right
      0 0 1 :down
      0 0.4 0.6 :down
      0 1 1 :left
      0 0.6 0.6 :left
      0 0.75 0.25 :right
      0 0.95 0.1 :right
      0 0.1 0.9 :down
      0 0.45 0.45 :up)))

(let [req-mov aip/request-movement
      motoception-off?
          (fn [player]
            (-> player ::mzs/senses ::mzs/input-vector mzs/motoception (= 0.0)))]
  (defn- request-and-store
    "Redefinition of `request-movement` to store player's movements in its data"
    [player-st world-st]
    (swap! player-st update :move-list
           conj [(-> @world-st ::aiw/game-step)
                 (-> @player-st :next-movement)
                 ;; movement considered RMR if motoception off
                 (motoception-off? @player-st)])
    (req-mov player-st world-st)))

;; Will become a randomness-reflex test
(deftest ^:integration m00-rmr-randomness
  :unstrumented
  (testing "Checks that over 1000 steps, 200 moves at least are
  made (because motoception persistence is 4, so at least a move every
  5 steps), and among the ones pertaining to RMR, 15% at least in each
  direction (where the perfect ratio would be 25% each)"
    (let [test-world (aiw/world 25 seed)
          steps 1000
          m00-opts {:seed seed :layer-dims (repeat 3 256)}
          game-opts
          (aim/parse-run-args "-v WARNING -n %d -t m00 -o'%s'" steps m00-opts)
          non-nil-movement? second]
      (with-redefs [aip/request-movement request-and-store]
        (let [move-list (-> (aim/run game-opts test-world) :player :move-list)
              rmr-moves (filter last move-list)]
          (is (<= 200 (count (filter non-nil-movement? move-list))))
          ;; less than 1 move on 20 is nil for rmr
          ;; (nil moves can happen when a1/a2 are very close to 0.5)
          (is (< (count (remove non-nil-movement? rmr-moves))
                 (/ (count rmr-moves) 20))) 
          ;; enough rmr moves to compute stats
          (is (<= 100 (count (filter second rmr-moves)))) 
          (is (every? #(> % (* (count rmr-moves) 0.15))
                      (map (frequencies (map second rmr-moves)) ge/directions)))
          (is (< 50 (count (remove non-nil-movement? move-list)))))))))
