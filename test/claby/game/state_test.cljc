(ns claby.game.state-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as st]
            [claby.utils
             #?(:clj :refer :cljs :refer-macros) [check-all-specs]]
            [claby.game.board :as gb]
            [claby.game.state :as g]))

(st/instrument)
(check-all-specs claby.game.state)

(def test-size 10)

(def test-state
  "A game with a test board of size 10, last line wall and before last
  line fruits, player at position [0 0]"
  (-> (gb/empty-board test-size)
      (assoc (- test-size 2) (vec (repeat test-size :fruit)))
      (assoc (- test-size 1) (vec (repeat test-size :wall)))
      (g/init-game-state)
      (assoc ::g/player-position [0 0])))

(deftest board-spec-test
  (testing "Player should not be able to be on a wall"
    (is (not (s/valid? ::g/game-state
                       (assoc test-state ::g/player-position [9 9]))))))

