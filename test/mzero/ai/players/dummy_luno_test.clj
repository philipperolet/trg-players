(ns mzero.ai.players.dummy-luno-test
  (:require [mzero.ai.players.dummy-luno :as sut]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [mzero.game.events :as ge]
            [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw]
            [mzero.game.generation :as gg]))

(deftest get-int-from-decimals
  (is (= 33 (#'sut/get-int-from-decimals 32.13325)))
  (is (= 72 (#'sut/get-int-from-decimals 0.3725))))

(defn run-n-steps
  [player size world acc]
  (if (> size 0)
    (let [next-movement
          (-> player (aip/update-player world) :next-movement)
          next-world
          (aiw/compute-new-state
           (assoc world ::aiw/requested-movements {:player next-movement}))]
      
      (recur player (dec size) next-world (conj acc next-movement)))
    acc))

(defn run-dummy-luno []
  (let [test-world
        (aiw/get-initial-world-state (first (gg/generate-game-states 1 25 41)))
        dummy-luno
        (aip/init-player (sut/->DummyLunoPlayer) nil test-world)]
    (u/timed (run-n-steps dummy-luno 1000 test-world []))))

(deftest dummy-luno-works
  (let [dl-updates (run-dummy-luno)]
    (testing "Random direction approximately correct, about 250 of each dir"
      (is (every? #(> % 220) (map (frequencies (dl-updates 1)) ge/directions))))

    (testing "Timing all right, less than 1 step per ms to run"
      (is (< (dl-updates 0) 1000)))))


