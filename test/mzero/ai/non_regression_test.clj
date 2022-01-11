(ns mzero.ai.non-regression-test
  (:require [clojure.test :refer [deftest is testing]]
            [mzero.ai.ann.activations :as mza]
            [mzero.ai.ann.initialization :as mzi]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.train :as mzt]
            [mzero.utils.utils :as u]
            [mzero.utils.xp :as xp]))

(def mean-res
  {(repeat 4 256) {mzi/draft1-sparse-weights 82.66666
                   mzi/angle-sparse-weights 101.66666}
   (repeat 3 512) {mzi/draft1-sparse-weights 84.33333
                   mzi/angle-sparse-weights 102.0}})

(deftest ^:integration refactoring-non-regression
  (testing "When refactoring with no param change and/or new features,
  score and fruit-move ratio should be exactly the same after a few
  runs.
  
  IF NEW FEAT / PARAM CHANGE, it MAY BE to change the result of this
  test, *after* having checked--via other means than this particular
  test--that:
  1/the change should indeed impact the result of this
  test, which hopefully should not happen too much;
  2/the change doesn't break anything"
    (doseq [test-seq
            [{:opts {:layer-dims (repeat 4 256)
                     :weights-generation-fn mzi/draft1-sparse-weights}
              :type "m00"
              :result 82.66666}
             {:opts {:layer-dims (repeat 3 512)
                     :weights-generation-fn mzi/angle-sparse-weights}
              :type "m00"
              :result 102.0}
             {:opts {:layer-dims (repeat 2 256)
                     :weights-generation-fn mzi/angle-sparse-weights}
              :type "m0-dqn"
              :result 87.0}]]
      (let [opts
            (merge (:opts test-seq)
                   {:ann-impl {:act-fns mza/usual
                               :label-distribution-fn mzld/softmax}})
            measurements
            (->> (mzt/run-games opts 3 27 (partial mzt/initial-players (:type test-seq)))
                 :game-measurements)]
        (is (u/almost= (:result test-seq)
                       (xp/mean (map :score measurements))) opts)))))

