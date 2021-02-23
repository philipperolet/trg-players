(ns mzero.ai.players.senses-test
  (:require [mzero.ai.players.senses :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest check-spec]]
            [mzero.game.state-test :as gst]
            [mzero.game.state :as gs]
            [mzero.game.generation :as gg]
            [mzero.game.board :as gb]))

(check-spec `sut/update-senses-data
            {:clojure.spec.test.check/opts {:num-tests 50}})


(check-spec `sut/new-satiety
            {:clojure.spec.test.check/opts {:num-tests 100}})

(deftest visible-matrix-test
  (let [{:keys [::gb/game-board ::gs/player-position]} gst/test-state-2]
    (is (= (#'sut/visible-matrix game-board player-position 1)
         [[:empty :wall :empty]
          [:fruit :empty :empty]
          [:empty :wall :empty]]))
    (is (= (#'sut/visible-matrix game-board [0 4] 1)
           [[:empty :empty :empty]
            [:empty :empty :empty]
            [:empty :empty :empty]]))))

(deftest visible-matrix-vector-test
  (let [{:keys [::gb/game-board ::gs/player-position]}
        (first (gg/generate-game-states 1 25 41))]
    (is (= (#'sut/visible-matrix-vector (#'sut/visible-matrix game-board player-position 2))
           (map float [0 0.5 0 0 0 0 0 0.5 0 0 0.5 0 0 0 0 0 1 1 1 1 1 1 1 1 1])))))

(deftest new-satiety-test
  (is (= (#'sut/new-satiety 0.0 7 8) 0.3))
  (is (= (#'sut/new-satiety 0.0415 9 9) 0.0))
  (is (= (#'sut/new-satiety 0.9 7 10) 1.0))
  (is (= (#'sut/new-satiety 0.5 0 0) (* 0.5 0.95))))
