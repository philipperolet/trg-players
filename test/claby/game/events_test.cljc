(ns claby.game.events-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as st]
            [claby.utils
             #?(:clj :refer :cljs :refer-macros) [check-all-specs]]
            [claby.game.board :as gb]
            [claby.game.state :as g]
            [claby.game.state-test :refer [test-state test-size]]
            [claby.game.events :as ge]))

(st/instrument)
(check-all-specs claby.game.events)

(deftest move-player-basic
  (testing "Moves correctly up, down, right, left on canonical
    board (see create game)"
    (let [test-state (assoc test-state ::g/player-position [1 0])]
      (are [x y] (= x (::g/player-position (ge/move-player test-state y)))
        [0 0] :up
        [1 1] :right
        [2 0] :down
        [1 (dec test-size)] :left)))

  (testing "Multiple movement tests"
    (are [x y] (= x (::g/player-position (ge/move-player-path test-state y)))
      [0 (- test-size 3)] [:left :left :left]
      [2 2] [:right :down :right :down]
      [0 0] [:left :down :up :right])))

(deftest move-player-walls
  (testing "Moves correctly when blocked by wall. Here (canonical
  board, position [0 0]) it means up is not possible, all other
  directions are, down then twice up blocks back to initial position."
    (are [x y] (= x (::g/player-position (ge/move-player test-state y)))
      [0 0] :up ;; blocked by wall on other side
      [0 1] :right
      [1 0] :down
      [0 (dec test-size)] :left)
    (is (= [0 0]
           (::g/player-position (ge/move-player-path test-state [:down :up :up])))))

  (testing "If encircled by walls, can't move"
    (let [test-state
          ;; creating test game with player encircled
          (-> test-state
              (assoc-in [::gb/game-board 0 1] :wall)
              (assoc-in [::gb/game-board 0 (dec test-size)] :wall)
              (assoc-in [::gb/game-board 1 0] :wall))]
      ;; blocked everywhere
      (are [x y] (= x (::g/player-position (ge/move-player test-state y)))
        [0 0] :up 
        [0 0] :right
        [0 0] :down
        [0 0] :left))))
      
(deftest move-player-fruits
  (testing "If player moves on fruits, fruit disappears and score goes
  up. Player goes all the way down, is blocked by wall, then eats 2
  fruits left and 3 right (6 total)"
    (is (every? #(= % :fruit) (get-in test-state [::gb/game-board 8])))
    
    (let [player-path (concat (repeat 10 :down) (repeat 2 :right) (repeat 5 :left))
          fruits-eaten-state (ge/move-player-path test-state player-path)
          fruit-row (get-in fruits-eaten-state [::gb/game-board 8])]
      (is (every? #(= % :fruit) (subvec fruit-row 3 7)))
      (is (every? #(= % :empty) (subvec fruit-row 7)))
      (is (every? #(= % :empty) (subvec fruit-row 0 3)))
      (is (= 6 (fruits-eaten-state ::g/score))))))

(deftest get-html-for-state-t
  (testing "Converts appropriately a board to reagent html"
    (is (= [:tbody
            [:tr {:key "claby-0"}
             [:td.empty {:key "claby-0-0"}]
             [:td.empty {:key "claby-0-1"}]
             [:td.wall {:key "claby-0-2"}]
             [:td.empty {:key "claby-0-3"}]
             [:td.empty {:key "claby-0-4"}]]
            [:tr {:key "claby-1"}
             [:td.empty {:key "claby-1-0"}]
             [:td.fruit {:key "claby-1-1"}]
             [:td.empty.player {:key "claby-1-2"}]
             [:td.empty {:key "claby-1-3"}]
             [:td.empty {:key "claby-1-4"}]]
            [:tr {:key "claby-2"}
             [:td.empty {:key "claby-2-0"}]
             [:td.empty {:key "claby-2-1"}]
             [:td.wall {:key "claby-2-2"}]
             [:td.empty {:key "claby-2-3"}]
             [:td.empty {:key "claby-2-4"}]]
            [:tr {:key "claby-3"}
             [:td.empty {:key "claby-3-0"}]
             [:td.empty {:key "claby-3-1"}]
             [:td.cheese {:key "claby-3-2"}]
             [:td.cheese {:key "claby-3-3"}]
             [:td.empty {:key "claby-3-4"}]]
            [:tr {:key "claby-4"}
             [:td.empty {:key "claby-4-0"}]
             [:td.empty {:key "claby-4-1"}]
             [:td.empty {:key "claby-4-2"}]
             [:td.empty {:key "claby-4-3"}]
             [:td.empty {:key "claby-4-4"}]]]

           (g/get-html-for-state
            {::g/status :active
             ::g/score 10
             ::gb/game-board [[:empty :empty :wall :empty :empty]
                             [:empty :fruit :empty :empty :empty]
                             [:empty :empty :wall :empty :empty]
                             [:empty :empty :cheese :cheese :empty]
                             [:empty :empty :empty :empty :empty]]
             ::g/player-position [1 2]})))))
