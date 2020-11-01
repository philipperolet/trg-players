(ns claby.ai.game-runner-test
  (:require [clojure.test]
            [claby.utils.testing :refer [deftest]]
            [claby.ai.main-test :refer [basic-run]]))

(deftest run-test-basic-monothreadrunner
  (basic-run "MonoThreadRunner"))

(deftest run-test-basic-watcherrunner
  (basic-run "WatcherRunner"))
