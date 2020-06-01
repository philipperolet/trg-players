(ns ^:figwheel-hooks claby.ux.lapy
  "Entry point to the game, with a creative ux for Lapyrinthe,
  aimed at making people play."
  (:require
   [clojure.test.check]
   [clojure.test.check.properties]
   [cljs.spec.alpha :as s]
   [cljs.spec.gen.alpha :as gen]
   [claby.ux.base :refer [mount-app-element]]))

(mount-app-element)

