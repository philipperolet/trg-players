(ns claby.game-test
  (:require [clojure.test :refer [testing deftest is]]
            [clojure.spec.alpha :as s]
            [claby.game :as g]))

(deftest create-game-test
  (testing "Creation of board"
    (is (s/valid? ::g/game-state (g/create-game)))))
