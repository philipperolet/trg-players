(ns claby.game-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [cljs.spec.gen.alpha :as gen]            
            [claby.game :as g]))

(deftest move-player-test
  (testing "Moves correctly up, down, right, left"
    (let [test-state {::g/game-board (gen/generate (s/gen ::g/game-board))
                      ::g/player-position [0 0]}]
      (let [size (count (::g/game-board test-state))]
        (are [x y] (= x (::g/player-position (g/move-player test-state y)))
          [(dec size) 0] :up
          [0 1] :right
          [1 0] :down
          [0 (dec size)] :left)))))

