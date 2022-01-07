(ns mzero.ai.players.m0-modules.m00-reinforcement-test
  (:require [mzero.ai.players.m0-modules.m00-reinforcement :as sut]
            [clojure.test :refer [is]]
            [mzero.utils.testing :refer [deftest check-spec]]))

(check-spec `sut/penalize-movement-distribution)
