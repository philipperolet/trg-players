(ns claby.core-test
    (:require
     [cljs.test :refer-macros [deftest is testing]]
     [clojure.test.check]
     [clojure.test.check.properties]
     [cljs.spec.test.alpha :as st]
     [claby.game :as g]
     [claby.core :as c]))

(deftest get-html-from-state-t
  (testing "Correct specs"
    (is (= nil (-> (st/check `c/get-html-from-state)
                   first
                   st/abbrev-result
                   (#(if (:failure %) (:failure %) nil))))))
  (testing "Converts appropriately a board to reagent html"
    (is (= [:table
            [:tr [:td.empty] [:td.empty] [:td.wall]]
            [:tr [:td.empty] [:td.fruit.player] [:td.empty]]
            [:tr [:td.empty] [:td.empty] [:td.empty]]]
           (c/get-html-from-state
            {::g/game-board [[:empty :empty :wall]
                             [:empty :fruit :empty]
                             [:empty :empty :empty]]
             ::g/player-position [1 1]})))))
