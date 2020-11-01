(ns claby.ai.players.tree-exploration-test
  (:require [claby.ai.players.tree-exploration :as sut]
            [clojure.test :refer [is testing]]
            [claby.utils.testing :refer [deftest]]
            [claby.ai.world :as aiw]
            [claby.game.state-test :as gst]
            [claby.game.events :as ge]
            [claby.game.state :as gs]
            [claby.ai.player :as aip]
            [claby.ai.main :as aim]
            [claby.game.board :as gb]
            [clojure.test.check.generators :as gen]
            [clojure.zip :as zip]))


(def world-state (-> gst/test-state-2
                     (assoc ::gs/player-position [2 0])
                     (assoc-in [::gb/game-board 4 2] :fruit)
                     aiw/get-initial-world-state))
(def initial-player
  (aip/init-player (sut/map->TreeExplorationPlayer {}) {:nb-sims 100} nil))

(deftest sum-children-frequencies-test
  (is (= 6 (sut/sum-children-frequencies {::sut/children
                                          {:up {::sut/frequency 2}
                                           :down {::sut/frequency 4}}}))))

(deftest update-children-test
  (let [test-node (reduce
                   #(sut/append-child %1 {::sut/frequency 1 ::ge/direction %2})
                   {::sut/frequency 3}
                   '(:up :down :right))
        updated-node (#'sut/update-children test-node)]
    (is (contains? (::sut/children updated-node) :right))
    (is (= (#'sut/update-children updated-node) updated-node))))

(deftest move-to-min-child-test
  (let [test-zipper
        (zip/vector-zip [{:a 8} [{:a 1} {:a 2} {:a 0} {:a 3} {:a 1}]])]
    (is (= {:a 0} (-> test-zipper
                      zip/down zip/right
                      (#'sut/move-to-min-child :a)
                      zip/node)))))

(deftest tree-exploration-player-test  
  (let [tree-root (-> initial-player
                      (aip/update-player world-state)
                      :root-node
                      sut/node)]
    (is (= (sut/sum-children-frequencies tree-root)
           100))
    (is (= ge/directions (set (keys (::sut/children tree-root)))))
    (is (every? #(= (::sut/frequency %) 25) (sut/children tree-root)))
    (is (= (-> tree-root ::sut/children :up ::sut/children :right ::sut/value) 0))
    (is (= (-> tree-root ::sut/children :up ::sut/value) 1))))

(deftest te-exploration-simulation-test
  (testing "Player should go eat the close fruit (up then right), then reset tree
    exploration from scratch--meaning after a simulation run the
    frequency of the root will be exactly the number of simlations"
    (let [{:keys [world player]}
          (aim/run (aim/parse-run-args "-t tree-exploration -n 2")
            world-state initial-player)

          root-node-after-sim
          (-> player (aip/update-player world) :root-node)]
      (is (= (-> world ::gs/game-state ::gs/player-position) [1 1]))
      (is (= (sut/sum-children-frequencies root-node-after-sim)
             (:nb-sims player))))))

(deftest ^:integration te-stability-test
  :unstrumented ;; speed test would be hindered by instrumentation
  (testing "2 tests in 1 : stackoverflow bug and speed

    1/ ERROR: recursivity in tree-simulate should not throw
    stackoverflow even big boards

    2/ FAILURE: it should be faster than 50 sims/sec even on big boards"
    (let [expected-sims-per-sec 80
          board-size 50, nb-steps 4, sims-per-step 80
          time-to-run-ms
          (* nb-steps sims-per-step (/ 1000 expected-sims-per-sec))
          initial-world ;; seeded generator, always same board
          (aiw/get-initial-world-state
           (gen/generate (gs/game-state-generator board-size) 100 3))
          game-result
          (future
            (aim/run
              (aim/parse-run-args "-t tree-exploration -n %d -o '{:nb-sims %d}'"
                                  nb-steps sims-per-step)
              initial-world))]
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
