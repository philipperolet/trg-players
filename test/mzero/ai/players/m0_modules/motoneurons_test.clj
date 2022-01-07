(ns mzero.ai.players.m0-modules.motoneurons-test
  (:require [mzero.ai.players.m0-modules.motoneurons :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest check-spec]]
            [mzero.utils.utils :as u]))

(check-spec `sut/motoneurons-weights
            {:clojure.spec.test.check/opts {:num-tests 100}})
(defn- almost0
  [x]
  (let [zero-threshold 1E-12]
    (< (Math/abs x) zero-threshold)))

(deftest motoneurons-weight-fn-test
  (is (almost0 (apply + (flatten (#'sut/motoneurons-weights 10 10 0)))))
  (is (almost0 (apply + (flatten (#'sut/motoneurons-weights 10 9 0)))))
  (is (almost0 (apply + (flatten (#'sut/motoneurons-weights 9 10 0)))))
  (is (u/almost= 0.33333 (apply + (flatten (#'sut/motoneurons-weights 9 9 0)))))
  (is (u/almost= 27.0 (apply + (map #(Math/abs %) (flatten (#'sut/motoneurons-weights 9 9 0)))))))
