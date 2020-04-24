(ns claby.game-test
  (:require [clojure.test :refer [testing deftest is]]
            [clojure.spec.alpha :as s]
            [claby.game :refer [create-game]]))

(deftest create-game-test
  (testing "Creation of board"
    (is (s/valid? ::game-board (create-game)))))
