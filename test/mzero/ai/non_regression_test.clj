(ns mzero.ai.non-regression-test
  (:require [clojure.test :refer [deftest is testing]]
            [mzero.ai.ann.activations :as mza]
            [mzero.ai.ann.initialization :as mzi]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.train :as mzt]
            [mzero.utils.utils :as u]
            [mzero.utils.xp :as xp]))

(def mean-res
  {(repeat 4 256) {mzi/draft1-sparse-weights 88.33333
                   mzi/angle-sparse-weights 101.0}
   (repeat 3 512) {mzi/draft1-sparse-weights 72.6666
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
    (doseq [layer-dims [(repeat 4 256)
                        (repeat 3 512)]
            init-fn [mzi/draft1-sparse-weights mzi/angle-sparse-weights]]
      (let [opts {:layer-dims layer-dims
                  :weights-generation-fn init-fn
                  :ann-impl {:act-fns mza/usual
                             :label-distribution-fn mzld/softmax}}
            measurements
            (->> (mzt/run-games opts 3 27)
                 :game-measurements)]
        (is (u/almost= (get-in mean-res [layer-dims init-fn])
                       (xp/mean (map :score measurements))) opts)))))

