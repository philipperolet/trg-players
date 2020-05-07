(ns claby.game-test
  (:require [clojure.test :refer [testing deftest is are]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.utils
             #?(:clj :refer :cljs :refer-macros) [instrument-and-check-all]]
            [claby.game :as g]))

(instrument-and-check-all claby.game)

(def test-size 10)

(def small-test-board
  [[:empty :empty :wall :empty :empty]
   [:empty :fruit :empty :empty :empty]
   [:empty :empty :wall :empty :empty]
   [:empty :empty :empty :cheese :empty]
   [:empty :empty :fruit :cheese :empty]])

(def test-state
  "A game with a test board of size 10, last line wall and before last
  line fruits, player at position [0 0]"
  (-> (g/init-game-state (g/empty-board test-size))
      (assoc ::g/player-position [0 0])
      (assoc-in [::g/game-board (- test-size 2)]
                (vec (repeat test-size :fruit)))
      (assoc-in [::g/game-board (- test-size 1)]
                (vec (repeat test-size :wall)))))

(deftest board-spec-test
  (testing "Player should not be able to be on a wall"
    (is (not (s/valid? ::g/game-state
                       (assoc test-state ::g/player-position [9 9]))))))

(deftest board-stats-test
  (testing "Board stats work"
    (let [{:keys [fruit-density total-cells non-wall-cells]} (g/board-stats small-test-board)]
      (is (= 25 total-cells))
      (is (= 23 non-wall-cells))
      (is (= (-> 2 (* 100) (/ non-wall-cells) int) fruit-density))))
  (testing "Density only considers non-walls"
    (is (= 50 (:fruit-density (g/board-stats
                               [[:wall :fruit :wall :wall]
                                [:wall :wall :wall :wall]
                                [:wall :wall :empty :wall]
                                [:wall :wall :wall :wall]]))))))

(deftest get-closest-test
  (testing "Rets the closest int or nil"
    (are [coll i res] (= res (g/get-closest coll i))
      [7 4 3 8] 2 3
      [5 10 10 12] 7 5
      [3 4 5 6] 3 3
      [3 5 6] 4 5
      [] 3 nil)))
       
      
(deftest find-in-board-test
  (testing "Finds the correct positions on a small test board, position [0 0]"
    (are [expected pred] (= expected (g/find-in-board small-test-board pred))
      [0 0] #{:empty}
      [1 1] #{:fruit}
      [0 2] #{:wall}
      [0 2] #{:fruit :wall}))
  (testing "Finds the correct positions on a small test board, position [2 2]"
    (are [expected pred] (= expected (g/find-in-board small-test-board pred [2 2]))
      [2 3] #{:empty}
      [1 1] #{:fruit}
      [2 2] #{:wall}
      [2 2] #{:fruit :wall})))

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

(deftest get-html-for-state-t
  (testing "Converts appropriately a board to reagent html"
    (is (= [:table
            [:tbody
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
              [:td.empty {:key "claby-4-4"}]]]]

           (g/get-html-for-state
            {::g/score 10
             ::g/game-board [[:empty :empty :wall :empty :empty]
                             [:empty :fruit :empty :empty :empty]
                             [:empty :empty :wall :empty :empty]
                             [:empty :empty :cheese :cheese :empty]
                             [:empty :empty :empty :empty :empty]]
             ::g/player-position [1 2]})))))
