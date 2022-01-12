(ns mzero.ai.players.m0-modules.senses-test
  (:require [clojure.test :refer [are is testing]]
            [mzero.ai.main :as aim]
            [mzero.ai.players.m0-modules.senses :as sut]
            [mzero.ai.world :as aiw :refer [world]]
            [mzero.game.board :as gb]
            [mzero.game.generation :as gg]
            [mzero.game.state :as gs]
            [mzero.utils.testing :refer [check-spec deftest]]
            [mzero.utils.utils :as u]
            [mzero.ai.players.m00 :as m00]
            [mzero.ai.players.m0-modules.m00-reinforcement :as m00r]
            [clojure.data.generators :as g]
            [mzero.ai.players.m00-test :refer [reference-world reference-player]]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [clojure.spec.alpha :as s]))

(check-spec `sut/update-senses-data
            {:clojure.spec.test.check/opts {:num-tests 50}})


(check-spec `sut/new-satiety
            {:clojure.spec.test.check/opts {:num-tests 100}})

(def test-state-2
  {::gs/status :active
   ::gs/score 10
   ::gs/enemy-positions [[0 0] [1 4]]
   ::gb/game-board [[:empty :empty :wall :empty :empty]
                    [:empty :fruit :empty :empty :empty]
                    [:empty :empty :wall :empty :empty]
                    [:empty :empty :cheese :cheese :empty]
                    [:empty :empty :empty :empty :empty]]
   ::gs/player-position [1 2]})

(deftest visible-matrix-test
  (with-redefs [sut/vision-depth 1
                sut/visible-matrix-edge-size 3]
    (let [{:keys [::gb/game-board ::gs/player-position]} test-state-2]
      (is (= (#'sut/visible-matrix game-board player-position)
             [[:empty :wall :empty]
              [:fruit :empty :empty]
              [:empty :wall :empty]]))
      (is (= (#'sut/visible-matrix game-board [0 4])
             [[:empty :empty :empty]
              [:empty :empty :empty]
              [:empty :empty :empty]])))))

(deftest visible-matrix-vector-test
  (with-redefs [sut/vision-depth 2
                sut/visible-matrix-edge-size 5]
    (let [{:keys [::gb/game-board ::gs/player-position]}
          (first (gg/generate-game-states 1 25 41))]
      (is (= (#'sut/visible-matrix-vector (#'sut/visible-matrix game-board player-position))
             (map float [0 0.5 0 0 0 0 0 0.5 0 0 0.5 0 0 0 0 0 1 1 1 1 1 1 1 1 1]))))))

(deftest new-satiety-test
  (is (= (#'sut/new-satiety 0.0 7 8 20) 0.3))
  (is (= (#'sut/new-satiety 0.0415 9 9 20) 0.0))
  (is (= (#'sut/new-satiety 0.9 7 10 20) 1.0))
  (is (u/almost= (#'sut/new-satiety 0.5 0 0 20) (* 0.5 0.950875))))

(deftest new-motoception-test
  (testing "Specs of motoception"
    (are [old brain-tau req-mov res]
        (u/almost= (#'sut/new-motoception old brain-tau req-mov) res)
      0.0 5 false 0.0
      0.0 5 true 1.0
      0.99 5 true 1.0
      1.0 10 false 0.995
      0.995 20 false 0.9925)))

(deftest motoception-computation-works
  (testing "Check the decrement system works. If brain-tau is 4, given
  an initial motoception of 1, applying 5 times motoception without
  movement should set it to 0. Carefulness is de rigueur because of
  float precision & relying on exact comparisons"
    (is (= 0.0 (nth (iterate #(#'sut/new-motoception % 4 false) 1.0) 4)))))

(deftest motoception-in-senses-test
  (let [player-options "{:seed 40 :layer-dims [100 100]}"
        run-args
        #(aim/parse-run-args "-v WARNING -t m00 -n %s -o'%s'" % player-options)
        {:keys [world player]}
        (with-redefs [m00/make-move #(assoc % :next-movement :up)]
          (aim/run (run-args 2) (world 25 41)))
        motopersistent-player
        (-> (assoc-in player [::sut/senses ::sut/params ::sut/brain-tau] 10))
        up-blocked-world
        (assoc-in world [::gs/game-state ::gs/player-position] [2 2])]
    (testing "During the 0-1 step there was a move. Therefore, during
    the 1-2 step, motoception is at 1"
      (is (= (sut/motoception (-> player ::sut/senses ::sut/input-vector)) 1.0)))
    (testing "After 5 iterations without move requests, motoception is
    down according to its computation method. After 10 iters, it is 0.0."
      (with-redefs [m00/make-move #(assoc % :next-movement nil)]
        ;; NOTE : ATTOW there is a move during the 1-2 step, so
        ;; motoception starts decreasing one step later.  IF TEST FAILS IN
        ;; FUTURE with wrong motoception values it may be because
        ;; changes made a move occur during step 1-2. In that case
        ;; motoception will start decreasing during step 2-3 instead of
        ;; being reset at 1
        (let [iter-1 (aim/run (run-args 1) world (assoc motopersistent-player :next-movement nil))
              iter-5 (aim/run (run-args 5) world (:player iter-1))
              iter-10 (aim/run (run-args 5) (:world iter-5) (:player iter-5))
              senses-of-iter #(-> % :player ::sut/senses ::sut/input-vector)]
          (is (u/almost= 0.975 (sut/motoception (-> iter-5 senses-of-iter))))
          (is (= 0.0 (sut/motoception (-> iter-10 senses-of-iter))))))
      (testing "If moving on a wall, motoception not on. If moving
      apart from wall, motoception on"
        (with-redefs [m00/make-move #(assoc % :next-movement :up)
                      ;; disable backward pass
                      m00r/backward-pass (fn [p _ _] p)]
          ;; constantly moving up, yet motoception is at 0 because of the wall
          (let [updated-player (->> motopersistent-player
                                    (aim/run (run-args 11) up-blocked-world)
                                   :player) ]
            (are [steps] (= 0.0 (->> updated-player
                                     (aim/run (run-args steps) up-blocked-world)
                                     :player ::sut/senses ::sut/input-vector
                                     sut/motoception))
              2 3 4)
            (with-redefs [m00/make-move #(assoc % :next-movement :right)]
              (are [steps] (= 1.0 (->> updated-player
                                       (aim/run (run-args steps) up-blocked-world)
                                       :player ::sut/senses ::sut/input-vector
                                       sut/motoception))
                2 3 4))))))))


(deftest new-aleaception-test
  (testing "Generate correctly 2 random 0-1 floats, properly seeded"
    (binding [g/*rnd* (java.util.Random. 41)]
      (is (= (#'sut/new-aleaception) [0.727294921875 0.8617618680000305]))
      (is (= (#'sut/new-aleaception)  [0.19185662269592285 0.4512230157852173])))))

(deftest ^:integration update-senses-test
  :unstrumented
  (with-redefs [sut/vision-depth 2
                sut/visible-matrix-edge-size 5
                sut/input-vector-size 29
                sut/motoception-index 25
                sut/satiety-index 26
                sut/aleaception-index 27]
    (let [player-options
          {:seed 40 :layer-dims [256 256]}
          run-args
          (aim/parse-run-args "-v WARNING -t m00 -n 1 -o'%s'"
                              player-options)
          {:keys [world player]}
          (aim/run run-args (world 25 41 false {::gg/density-map {:fruit 5}}))
          world (assoc-in world [::gs/game-state ::gs/player-position] [1 11])]
      (testing "Correct update of senses data in player"
        ;; new vision is as follows
        ;; |  #o |
        ;; |  # o|
        ;; |  @  |
        ;; |     |
        ;; |  o  |
        (binding [g/*rnd* (:rng player)]
          (let [updated-senses
                (-> (::sut/senses player)
                    (sut/update-senses world player)
                    (dissoc ::sut/params))]
            
            (is (= (::sut/input-vector updated-senses)
                   (vec (concat [0.0 0.0 1.0 0.5 0.0]
                                                   [0.0 0.0 1.0 0.0 0.5]
                                                   [0.0 0.0 0.0 0.0 0.0]
                                                   [0.0 0.0 0.0 0.0 0.0]
                                                   [0.0 0.0 0.5 0.0 0.0]
                                                   [1.0 0.0]
                                                   [0.4493142366409302
                                                    0.39644312858581543]))))
            (is (=  (::sut/data updated-senses)
                    {::sut/previous-score 0
                     ::sut/last-position [5 12]
                     ::sut/previous-datapoints
                     '({::sut/state [0.0 0.5 0.0 0.0 0.0 0.0 0.0 0.5 0.0 0.0 0.5 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 0.0 0.0 0.5927296876907349 0.0223122239112854]
                        ::sut/action :left
                        ::sut/reward 0.0}
                       {::sut/state [0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0]
                        ::sut/action nil
                        ::sut/reward -0.1})
                     ::gs/game-state (::gs/game-state world)}))))))))

(deftest vision-cell-index
  (is (= (sut/vision-cell-index [0 0])
         (+ (* sut/visible-matrix-edge-size sut/vision-depth) sut/vision-depth)))
  (is (= (sut/vision-cell-index [(- sut/vision-depth) (- sut/vision-depth)]) 0))
  (is (= (sut/vision-cell-index [1 0])
         (+ (* (inc sut/vision-depth) sut/visible-matrix-edge-size) sut/vision-depth)))
  (is (= (sut/vision-cell-index [0 1])
         (+ (* sut/vision-depth sut/visible-matrix-edge-size) (inc sut/vision-depth))))
  (is (= (sut/vision-cell-index [sut/vision-depth sut/vision-depth])
         (dec (* sut/visible-matrix-edge-size sut/visible-matrix-edge-size)))))

(deftest stm-input-vector-test
  (let [previous-dps '({::sut/state [1 2] ::sut/action :up}
                       {::sut/state [3 4] ::sut/action :down}
                       {::sut/state [4 5] ::sut/action :left}
                       {::sut/state [5 6] ::sut/action :left})
        input-vector [7 7]]
    (is (= [[7 7] [1 2] [3 4] [4 5]] (sut/stm-input-vector previous-dps input-vector)))))
