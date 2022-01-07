(ns mzero.utils.random
  (:require [clojure.data.generators :as g]))

(defn seeded-seeds
  "Return seeds generated from a seed"
  ([seed start stop]
   (binding [g/*rnd* (java.util.Random. seed)]
     (->> (repeatedly g/long)
          (drop start)
          (take stop)
          vec))))

