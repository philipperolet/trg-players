(ns claby.core-test
    (:require
     [cljs.test :refer-macros [deftest is testing]]
     [clojure.test.check]
     [clojure.test.check.properties]
     [cljs.spec.test.alpha :as st]
     [claby.game :as g]
     [claby.core :as c]))

(st/instrument)

(deftest test-all-specs
  (is (= () (->> (st/check)
                  (map #(select-keys (st/abbrev-result %) [:failure :sym]))
                  (remove #(not (:sym %)))
                  ;; if spec conformance test failed, returns failure data 
                  (filter #(:failure %))))))


(deftest get-html-for-state-t
  (testing "Converts appropriately a board to reagent html"
    (is (= [:table
            [:tbody
             [:tr {:key "claby-0"}
              [:td.empty {:key "claby-0-0"}]
              [:td.empty {:key "claby-0-1"}]
              [:td.wall {:key "claby-0-2"}]
              [:td.empty {:key "claby-0-3"}]
              [:td.empty {:key "claby-0-4"}]]
             [:tr {:key "claby-1"}
              [:td.empty {:key "claby-1-0"}]
              [:td.fruit {:key "claby-1-1"}]
              [:td.empty.player {:key "claby-1-2"}]
              [:td.empty {:key "claby-1-3"}]
              [:td.empty {:key "claby-1-4"}]]
             [:tr {:key "claby-2"}
              [:td.empty {:key "claby-2-0"}]
              [:td.empty {:key "claby-2-1"}]
              [:td.wall {:key "claby-2-2"}]
              [:td.empty {:key "claby-2-3"}]
              [:td.empty {:key "claby-2-4"}]]
             [:tr {:key "claby-3"}
              [:td.empty {:key "claby-3-0"}]
              [:td.empty {:key "claby-3-1"}]
              [:td.empty {:key "claby-3-2"}]
              [:td.empty {:key "claby-3-3"}]
              [:td.empty {:key "claby-3-4"}]]
             [:tr {:key "claby-4"}
              [:td.empty {:key "claby-4-0"}]
              [:td.empty {:key "claby-4-1"}]
              [:td.empty {:key "claby-4-2"}]
              [:td.empty {:key "claby-4-3"}]
              [:td.empty {:key "claby-4-4"}]]]]

           (c/get-html-for-state
            {::g/game-board [[:empty :empty :wall :empty :empty]
                             [:empty :fruit :empty :empty :empty]
                             [:empty :empty :wall :empty :empty]
                             [:empty :empty :empty :empty :empty]
                             [:empty :empty :empty :empty :empty]]
             ::g/player-position [1 2]})))))
