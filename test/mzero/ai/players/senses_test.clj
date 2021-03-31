(ns mzero.ai.players.senses-test
  (:require [mzero.ai.players.senses :as sut]
            [mzero.ai.players.dummy-luno :as dl]
            [mzero.ai.world :as aiw :refer [world]]
            [clojure.test :refer [is testing are]]
            [mzero.utils.testing :refer [deftest check-spec]]
            [mzero.game.state-test :as gst]
            [mzero.game.state :as gs]
            [mzero.game.generation :as gg]
            [mzero.game.board :as gb]
            [mzero.ai.main :as aim]
            [mzero.utils.utils :as u]))

(check-spec `sut/update-senses-data
            {:clojure.spec.test.check/opts {:num-tests 50}})


(check-spec `sut/new-satiety
            {:clojure.spec.test.check/opts {:num-tests 100}})

(deftest visible-matrix-test
  (with-redefs [sut/vision-depth 1
                sut/visible-matrix-edge-size 3]
    (let [{:keys [::gb/game-board ::gs/player-position]} gst/test-state-2]
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
    (are [old mot-per req-mov res]
        (u/almost= (#'sut/new-motoception old mot-per req-mov) res)
      0.0 5 nil 0.0
      0.0 5 :left 1.0
      0.99 5 :right 1.0
      1.0 5 nil 0.995
      0.995 10 nil 0.9925)))

(deftest motoception-in-senses-test
  (let [player-options "{:seed 40}"
        run-args
        (aim/parse-run-args "-v WARNING -t dummy-luno -n 5 -o'%s'" player-options)
        {:keys [world player]} (aim/run run-args (world 25 41))]

    (testing "There has just been a movement, motoception on"
      (is (= (sut/motoception (-> player :senses ::sut/input-vector)) 1.0)))

    (testing "After 5 iterations without move requests, motoception is
    down. After 10, it is 0."
      (with-redefs [dl/new-direction (constantly nil)]
        (let [motopersistant-player
              (-> player
                  (assoc-in [:senses-data ::sut/brain-tau] 5)
                  (assoc :next-movement nil))
              iter-5 (aim/run run-args world motopersistant-player)
              iter-10 (aim/run run-args (:world iter-5) (:player iter-5))
              senses-of-iter #(-> % :player :senses ::sut/input-vector)]
          (is (u/almost= 0.975 (sut/motoception (-> iter-5 senses-of-iter))))
          (is (= 0.0 (sut/motoception (-> iter-10 senses-of-iter)))))))))

(deftest ^:integration update-senses-test
  (with-redefs [sut/vision-depth 2
                sut/visible-matrix-edge-size 5
                sut/input-vector-size 27]
    (let [player-options
          "{:seed 40}"
          run-args
          (aim/parse-run-args "-v WARNING -t dummy-luno -n 20 -o'%s'"
                              player-options)
          {:keys [world player]}
          (aim/run run-args (world 25 41))]
      (testing "Correct update of senses data in player"
        ;; new vision is as follows
        ;; |     |
        ;; |     |
        ;; |  @ o|
        ;; |#  ##|
        ;; |# ###|
        (is (= (sut/update-senses (:senses player) world player)
               #::sut{:input-vector (vec (concat (repeat 14 0.0)
                                                 [0.5 1.0 0.0 0.0 1.0 1.0]
                                                 [1.0 0.0 1.0 1.0 1.0]
                                                 [1.0 0.24525345171117205]))
                      :params {::sut/brain-tau 5}
                      :data {::sut/previous-score 1
                             ::sut/last-move :right
                             ::gs/game-state (::gs/game-state world)}}))))))

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
