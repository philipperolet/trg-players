(ns mzero.ai.ann.losses-test
  (:require [mzero.ai.ann.losses :as sut]
            [clojure.test :refer [are is]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.ann.common :as mzc]))

(deftest cel-gradient-test
  (are [motos-tensor ldf tdf res]
      (= (#'sut/cross-entropy-loss-gradient ldf motos-tensor tdf) res)
    [[50.0 0.0 -10.0] [0.0 30.0 30.0]] mzld/softmax
    [[1.0 0.0 0.0] [1.0 0.0 0.0]] [[0.0 0.0 0.0] [-1.0 0.5 0.5]]
    
    [[0.0 50.0 50.0 50.0 50.0]] mzld/softmax
    [[0.0 0.0 nil 0.25 0.75]] [[0.0 0.25 0.0 0.0 -0.5]]))

(deftest mse-gradient-test
  (is (mzc/coll-almost= (#'sut/mse-loss-gradient [[0.4 0.5 0.5] [1.0 1.0 1.0]]
                                  [[nil nil 0.1] [nil 1.3 0.2]])
         [[0.0 0.0 0.4] [0.0 -0.3 0.8]])))
