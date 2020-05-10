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
      (g/init-game-state 0)
      (assoc ::g/player-position [0 0])))

(deftest board-spec-test
  (testing "Player should not be able to be on a wall"
    (is (not (s/valid? ::g/game-state
                       (assoc test-state ::g/player-position [9 9]))))))

(deftest get-html-for-state-t
  (testing "Converts appropriately a board to reagent html"
    (is (= [:tbody
            [:tr {:key "claby-0"}
             [:td.empty.enemy-0 {:key "claby-0-0"}]
             [:td.empty {:key "claby-0-1"}]
             [:td.wall {:key "claby-0-2"}]
             [:td.empty {:key "claby-0-3"}]
             [:td.empty {:key "claby-0-4"}]]
            [:tr {:key "claby-1"}
             [:td.empty {:key "claby-1-0"}]
             [:td.fruit {:key "claby-1-1"}]
             [:td.empty.player {:key "claby-1-2"}]
             [:td.empty {:key "claby-1-3"}]
             [:td.empty.enemy-1 {:key "claby-1-4"}]]
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
             ::g/enemy-positions [[0 0] [1 4]]
             ::gb/game-board [[:empty :empty :wall :empty :empty]
                             [:empty :fruit :empty :empty :empty]
                             [:empty :empty :wall :empty :empty]
                             [:empty :empty :cheese :cheese :empty]
                             [:empty :empty :empty :empty :empty]]
             ::g/player-position [1 2]})))))
  
