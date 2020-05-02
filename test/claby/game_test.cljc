(ns claby.game-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as st]
            [claby.utils :refer [check-results check-all-specs check-failure]]
            [claby.game :as g]))

(st/instrument (st/enumerate-namespace 'claby.game))
#_(deftest check-specs
  (testing "TEst 1"
    (is (= () (check-results (st/enumerate-namespace 'claby.game))))))

(check-all-specs claby.game)

#_(deftest prep-macro
  (is (= nil (check-failure claby.game/move-position))))

(def test-size 10)

(def test-state
  "A game with a test board of size 10, last line wall and before last
  line fruits."
  (-> (g/init-game-state test-size)
      (assoc-in [::g/game-board (- test-size 2)]
                (vec (repeat test-size :fruit)))
      (assoc-in [::g/game-board (- test-size 1)]
                (vec (repeat test-size :wall)))))

(deftest board-spec-test
  (testing "Player should not be able to be on a wall"
    (is (not (s/valid? ::g/game-state
                       (assoc test-state ::g/player-position [9 9]))))))

(deftest move-player-basic
  (testing "Moves correctly up, down, right, left on canonical
    board (see create game)"
    (let [test-state (assoc test-state ::g/player-position [1 0])]
      (are [x y] (= x (::g/player-position (g/move-player test-state y)))
        [0 0] :up
        [1 1] :right
        [2 0] :down
        [1 (dec test-size)] :left)))

  (testing "Multiple movement tests"
    (are [x y] (= x (::g/player-position (g/move-player-path test-state y)))
      [0 (- test-size 3)] [:left :left :left]
      [2 2] [:right :down :right :down]
      [0 0] [:left :down :up :right])))

(deftest move-player-walls
  (testing "Moves correctly when blocked by wall. Here (canonical
  board, position [0 0]) it means up is not possible, all other
  directions are, down then twice up blocks back to initial position."
    (are [x y] (= x (::g/player-position (g/move-player test-state y)))
      [0 0] :up ;; blocked by wall on other side
      [0 1] :right
      [1 0] :down
      [0 (dec test-size)] :left)
    (is (= [0 0]
           (::g/player-position (g/move-player-path test-state [:down :up :up])))))

  (testing "If encircled by walls, can't move"
    (let [test-state
          ;; creating test game with player encircled
          (-> test-state
              (assoc-in [::g/game-board 0 1] :wall)
              (assoc-in [::g/game-board 0 (dec test-size)] :wall)
              (assoc-in [::g/game-board 1 0] :wall))]
      ;; blocked everywhere
      (are [x y] (= x (::g/player-position (g/move-player test-state y)))
        [0 0] :up 
        [0 0] :right
        [0 0] :down
        [0 0] :left))))
      
(deftest move-player-fruits
  (testing "If player moves on fruits, fruit disappears and score goes
  up. Player goes all the way down, is blocked by wall, then eats 2
  fruits left and 3 right (6 total)"
    (is (every? #(= % :fruit) (get-in test-state [::g/game-board 8])))
    
    (let [player-path (concat (repeat 10 :down) (repeat 2 :right) (repeat 5 :left))
          fruits-eaten-state (g/move-player-path test-state player-path)
          fruit-row (get-in fruits-eaten-state [::g/game-board 8])]
      (is (every? #(= % :fruit) (subvec fruit-row 3 7)))
      (is (every? #(= % :empty) (subvec fruit-row 7)))
      (is (every? #(= % :empty) (subvec fruit-row 0 3)))
      (is (= 6 (fruits-eaten-state ::g/score))))))

(deftest generate-wall-randomly
  (testing "that walls are generated somewhat randomly")
  (let [[wall1 wall2 wall3]
        (repeatedly 3 #(g/generate-wall test-size (- test-size 3)))]
    
    (are [x y] (and (not= (x 0) (y 0)) (not= (x 1) (y 1)))
      wall1 wall2
      wall1 wall3
      wall2 wall3)))

(deftest add-wall-correctly
  (let [test-board (test-state ::g/game-board)
        wall [[1 1] [:right :right :right :up :up]]
        walled-board (g/add-wall test-board wall)]
    (is (every? #(= (test-board %) (walled-board %)) (range 2 test-size)))
    (is (every? #(= :wall %) (subvec (walled-board 1) 1 5)))
    (is (= :wall (get-in walled-board [0 4])))))
