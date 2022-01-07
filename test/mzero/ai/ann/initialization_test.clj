(ns mzero.ai.ann.initialization-test
  (:require [clojure.test :refer [are is testing]]
            [mzero.ai.ann.common :as mzc]
            [mzero.ai.ann.initialization :as sut]
            [mzero.utils.testing :refer [check-spec deftest]]
            [mzero.utils.utils :as u]))

(check-spec `sut/draft1-sparse-weights
            {:clojure.spec.test.check/opts {:num-tests 100}})
(check-spec `sut/almost-empty-weights
            {:clojure.spec.test.check/opts {:num-tests 100}})
(check-spec `sut/angle-sparse-weights
            {:clojure.spec.test.check/opts {:num-tests 100}})

(def seed 44)
(deftest sparsify-test
  :unstrumented
  (let [input-dim 1024 nb-cols 1000
        w (#'sut/draft1-sparse-weights input-dim nb-cols seed)
        nb-nonzero-weights (count (filter (comp not zero?) (flatten w)))
        nb-neg-weights (count (filter neg? (flatten w)))
        neg-ratio (/ nb-neg-weights nb-nonzero-weights)
        total-ratio (/ nb-nonzero-weights (* (count (first w)) (count w)))
        expected-total-ratio (/ (#'sut/nonzero-weights-nb input-dim 0.5) input-dim)]
    (is (u/almost= neg-ratio sut/neg-weight-ratio (* 0.05 neg-ratio)))
    (is (every?
         (fn [col]
           (u/almost= sut/max-init-activation
                      (apply + (filter pos? col))))
         (mzc/transpose w)))
    (is (u/almost= total-ratio expected-total-ratio (* 0.05 total-ratio)))))


(deftest normalized-weights-test
  (testing "Return correct values for a few cases"
    (are [w phi res] (u/coll-almost= (#'sut/normalized-weights w phi) res)
      ;; norm is most of the times 1
      [-1 0 0] (/ Math/PI 4) [-1 0 0]
      [0 0 1] (/ Math/PI 4) [0 0 1]
      ;; 0.5 / (1/sqrt(2))
      [0 -0.5 0 0.5 0] (/ Math/PI 4) [0 (* -0.5 (Math/sqrt 2)) 0 (* 0.5 (Math/sqrt 2)) 0]
      
      [1 0 0] (/ Math/PI 3) [(Math/sqrt 3) 0 0]
      [0 0 -1] (/ Math/PI 3) [0 0 (- (Math/sqrt 3))]
      ;; 0.5 / (1/ sqrt(2)) * sqrt(3)
      [0 0.5 0 0.5 0] (/ Math/PI 3) [0 (* 0.5 (Math/sqrt 6)) 0 (* 0.5 (Math/sqrt 6)) 0] 
      ;; norme = sqrt(10)/4
      [0 -0.25 0.75 0] (/ Math/PI 4) [0 (/ -0.25 (Math/sqrt 10) 0.25) (/ 0.75 (Math/sqrt 10) 0.25) 0]))) 

(deftest bias-test
  (testing "Return correct values for a few cases"
    (are [snw jp1 K res] (u/almost= (#'sut/bias snw jp1 K 0.25) res)
      ;; init value is scaled to 0.3
      ;; wsum is 0.31, init scaling 0.135
      [-0.45 0.38 -0.07] 3 0.0 -0.175
      ;; wsum is 0.56, init scaling -0.156
      [0.52 0.56 0.996 0.84] 2 1.0 0.284
      ;; wsum is -0.12, init scaling 0.21
      [-0.70 -0.12 -0.13 0.5] 2 0.0 0.33
      ;; wsum is 0, init scaling 0.135
      [-0.45 0.38 -0.07] 1 0.0 0.135
      ;; wsum is 2.396, is -0.156
      [0.52 0.56 0.996 0.84] 4 1.0 -1.552
      ;; wsum is -0.25, is 0.21
      [-0.70 -0.12 -0.13 0.5] 3 0.0 0.46)))
