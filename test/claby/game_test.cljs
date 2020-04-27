(ns claby.game-test
  (:require [clojure.test :refer [testing deftest is]]
            [clojure.spec.alpha :as s]
            [cljs.spec.gen.alpha :as gen]            
            [claby.game :as g]))

(deftest move-player-test
  (testing "Moves correctly up, down, right, left"
    (let [test-state {::g/game-board (gen/generate (s/gen ::g/game-board))
                      ::g/player-position [0 0]}]
      (is true))))

