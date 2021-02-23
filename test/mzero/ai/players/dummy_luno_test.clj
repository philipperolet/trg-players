(ns mzero.ai.players.dummy-luno-test
  (:require [mzero.ai.players.dummy-luno :as sut]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [mzero.game.events :as ge]
            [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw]
            [mzero.game.generation :as gg]
            [mzero.ai.main :as aim]
            [mzero.ai.players.senses :as ps]
            [uncomplicate.neanderthal.native :refer [dge]]))

(defn world
  "Get a test world given board `size`, and `seed`"
  [size seed]
  (aiw/get-initial-world-state
   (first (gg/generate-game-states 1 size seed))))

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

(deftest forward-pass-correctness
  (let [input (dge 1 3 [1.0 0.5 2.0])
        hidden (dge 3 4 [0 0 0 1 1 1 -1 0.5 3.2 0 0 0])
        output (dge 4 1 [0.5 -0.5 3 -7])]
    (is (u/almost= (#'sut/forward-pass input hidden output) 15.2 0.00001))))

(deftest dummy-luno-randomness
  (let [test-world (world 25 41)
        dummy-luno
        (aip/load-player "dummy-luno" {:seed 40} test-world)
        dl-updates
        (u/timed (run-n-steps dummy-luno 1000 test-world []))]
    
    (testing "Random direction approximately correct, about 250 of each dir"
      (is (every? #(> % 220) (map (frequencies (dl-updates 1)) ge/directions))))))

(deftest ^:integration dummy-luno-fast-enough
  :unstrumented
  (testing "Fast enough, more than 1K cycles/s on a size 50 board for 1 layer"
    (let [player-options
          "{:hidden-layer-size 10000 :seed 40}"
          run-args
          (aim/parse-run-args "-v WARNING -t dummy-luno -n 1000 -o'%s'"
                              player-options)
          runtime
          (first (u/timed (aim/run run-args (world 50 41))))]
      (is (< runtime 1000)))))

(deftest dl-update-player-test
  (let [player-options "{:seed 40 :vision-depth 2}"
        run-args
        (aim/parse-run-args "-v WARNING -t dummy-luno -n 18 -o'%s'" player-options)
        {:keys [world player]} (aim/run run-args (world 25 41))]
    (testing "Correct update of senses data in player"
      ;; new vision is as follows
      ;; |     |
      ;; |     |
      ;; |  @  |
      ;; | ####|
      ;; |###  |
      (is (= (:senses-data (aip/update-player player world))
             #::ps{:senses-vector (vec (concat (repeat 16 0.0)
                                               (repeat 7 1.0)
                                               [0.0 0.0]
                                               [0.3]))
                   :vision-depth 2
                   :previous-score 1})))))
