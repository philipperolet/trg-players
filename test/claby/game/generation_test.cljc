(ns claby.game.generation-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as st]
            [claby.utils :refer [check-all-specs]]
            [claby.game-test :refer [test-size test-state]]
            [claby.game :as g]
            [claby.game.generation :as gg]))

(st/instrument (st/enumerate-namespace 'claby.game.generation))

(check-all-specs claby.game.generation)

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
