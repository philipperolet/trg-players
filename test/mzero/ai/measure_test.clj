(ns mzero.ai.measure-test
  (:require [mzero.ai.measure :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.game.state :as gs]
            [mzero.ai.world :as aiw]
            [mzero.ai.players.m00 :as m00]
            [mzero.utils.utils :as u]
            [mzero.ai.train :as mzt]))

" Test world : size 25, seed 25
---------------------------
|    o      oo   #  #     |
|   o        o   #  #  o o|
|  o          oo #########|
|  o       oo      #######|
|#o      ####    oo  ###o#|
|      oo#####@  oo    o #|
|  o     #####     o  o  #|
|    oo   # ###o          |
|   o    ######   o       |
|     o o #o####          |
|          o#             |
| o     o   #             |
|   o        o      o o   |
|               o   o     |
|     o       oo       oo |
|   o o ####   ###      o |
|      #####o  # #       o|
|     o## o### #o o   o   |
| o######  # ###     o  o |
|     ##  o#     oo    o  |
|   ###o   #       oo     |
|o   ##    o           o  |
| o   #       o  o        |
|   o        o   o       o|
|   o      o              |
---------------------------
"
(deftest step-measure
  (let [test-world (aiw/world 25 25)
        move-pos #(assoc-in test-world [::gs/game-state ::gs/player-position] %)]
    (doseq [[pos res res2 res3] '([[1 1] 0 0 1] [[1 2] 1 0 0]
                                  [[1 3] 0 0 1] [[1 4] 1 0 0]
                                  [[1 24] 0 1 1] [[1 23] 1 1 0]
                                  [[1 20] 0 1 1] [[7 24] 0 1 0])]
      (is (= res (get-in (#'sut/step-measure (move-pos pos) {})
                         [:step-measurements :nb-next-fruit])) pos)
      (is (= res3 (get-in (#'sut/step-measure (move-pos pos) {})
                         [:step-measurements :nb-2step-fruit])) pos)      
      (is (= res2 (get-in (#'sut/step-measure (move-pos pos) {})
                         [:step-measurements :nb-next-wall])) pos))
    (is (= 3 (get-in (#'sut/step-measure (move-pos [1 2]) {:step-measurements {:nb-next-fruit 2}})
                     [:step-measurements :nb-next-fruit])))))

(def moves-list (atom '(nil :right :left :left :up :down :left :left :left :left)))
(deftest nb-moved-wall-test
  :unstrumented
  "Move list above ensures 7 times next to wall, of which 4 times"
  (with-redefs [m00/make-move
                (fn [pl]
                  (swap! moves-list pop)
                  (assoc pl :next-movement (first @moves-list)))
                mzt/nb-steps-per-game 9
                aiw/world (constantly (aiw/world 25 25))]
    (let [result
          (-> (mzt/run-games {:layer-dims [128 128 128]} 1 42)
              :game-measurements)]
      (is (u/almost= (-> result first :wall-move-ratio) 4/7)))))
