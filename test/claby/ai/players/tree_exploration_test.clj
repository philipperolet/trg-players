(ns claby.ai.players.tree-exploration-test
  (:require [claby.ai.players.tree-exploration :as sut]
            [clojure.test :refer [deftest is testing]]
            [claby.ai.world :as aiw]
            [claby.game.state-test :as gst]
            [claby.game.events :as ge]
            [claby.game.state :as gs]
            [claby.ai.player :as aip]
            [clojure.spec.test.alpha :as st]
            [claby.ai.main :as aim]
            [claby.game.board :as gb]
            [clojure.test.check.generators :as g]))


(def world-state (-> gst/test-state-2
                     (assoc ::gs/player-position [2 0])
                     (assoc-in [::gb/game-board 4 2] :fruit)
                     aiw/get-initial-world-state))
(def initial-player
  (sut/->TreeExplorationPlayer 100))

(deftest sum-children-frequencies-test
  (is (= 6 (sut/sum-children-frequencies {::sut/children
                                          {:up {::sut/frequency 2}
                                           :down {::sut/frequency 4}}}))))
(deftest tree-exploration-player-test  
  (let [tree-root (-> initial-player
                      (aip/update-player world-state)
                      :root-node)]
    (is (= (sut/sum-children-frequencies tree-root)
           100))
    (is (= ge/directions (set (keys (::sut/children tree-root)))))
    (is (every? #(= (::sut/frequency %) 25) (vals (::sut/children tree-root))))
    (is (= (-> tree-root ::sut/children :up ::sut/children :right ::sut/value) 0))
    (is (= (-> tree-root ::sut/children :up ::sut/value) 1))))

(deftest te-exploration-simulation-test
  (testing "Player should go eat the close fruit (up then right), then reset tree
    exploration from scratch--meaning after a simulation run the
    frequency of the root will be exactly the number of simlations"
    (st/instrument)
    (let [{:keys [world player]}
          (aim/run (aim/parse-run-args "-t tree-exploration -n 2")
            world-state initial-player)

          root-node-after-sim
          (-> player (aip/update-player world) :root-node)]
      (is (= (-> world ::gs/game-state ::gs/player-position) [1 1]))
      (is (= (sut/sum-children-frequencies root-node-after-sim)
             (:nb-sims player))))))

(deftest ^:integration te-stability-test
  (testing "2 tests in 1 : stackoverflow bug and speed

    1/ ERROR: recursivity in tree-simulate should not throw
    stackoverflow even big boards

    2/ FAILURE: it should be faster than 50sims/sec even on big boards"
    (let [expected-sims-per-sec 20
          board-size 50, nb-steps 5, sims-per-step 200 
          time-to-run-ms
          (* nb-steps sims-per-step (/ 1000 expected-sims-per-sec))
          game-result
          (future
            (aim/run
              (aim/parse-run-args "-t tree-exploration -n %d -o '{:nb-sims %d}'"
                                  nb-steps sims-per-step)
              (aiw/get-initial-world-state ;; seeded generator, always same board
               (g/generate (gs/game-state-generator board-size) 100 3))))]
      (is (not (nil? (deref game-result time-to-run-ms nil)))
          (str "time > than " time-to-run-ms))))) 

#_(deftest ^:integration tree-exploration-player-run
  (testing "A game with tree-exploration be won in < 300 steps
  on a small 10*10 board"
    (let [game-result
          (future (aim/run
                    (aim/parse-run-args
                     "-t tree-exploration -s 10 -i -n 100 -o {:nb-sims 100}")))
          counter (atom 0)]
      ;; simulate interactivity commands to let the game run for 300 steps
      ;; continue 3 times then quit
      (with-redefs [clojure.core/read-line
                    (fn []
                      (Thread/sleep 1000)
                      (swap! counter inc)
                      (nth (cycle '("" "" "" "q")) @counter))]
        (is (= (-> @game-result ::gs/game-state ::gs/status) :won))))))
