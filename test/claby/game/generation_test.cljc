(ns claby.game.generation-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.utils
             #?(:clj :refer :cljs :refer-macros) [instrument-and-check-all]]
            [claby.game-test :refer [test-size test-state small-test-board]]
            [claby.game :as g]
            [claby.game.generation :as gg]))

(instrument-and-check-all claby.game.generation)

(deftest generate-wall-randomly
  (testing "that walls are generated somewhat randomly")
  (let [[wall1 wall2 wall3]
        (repeatedly 3 #(gg/generate-wall test-size (- test-size 3)))]
    
    (are [x y] (and (not= (x 0) (y 0)) (not= (x 1) (y 1)))
      wall1 wall2
      wall1 wall3
      wall2 wall3)))

(deftest add-wall-correctly
  (let [test-board (test-state ::g/game-board)
        wall [[1 1] [:right :right :right :up :up]]
        walled-board (gg/add-wall test-board wall)]
    (is (every? #(= (test-board %) (walled-board %)) (range 2 test-size)))
    (is (every? #(= :wall %) (subvec (walled-board 1) 1 5)))
    (is (= :wall (get-in walled-board [0 4])))))

(deftest sum-of-densities-basic-test
  (is (= 11 (gg/sum-of-densities (test-state ::g/game-board))))
  (is (= (int (/ 400 23)) (gg/sum-of-densities small-test-board))))

(deftest create-nice-board-test
  (testing "Appropriate densities in board"
    (let [board (gg/create-nice-board 5 {::gg/density-map {:fruit 5 :cheese 10}})]
      (is (gg/valid-density board :fruit 5))
      (is (gg/valid-density board :cheese 10)))))
