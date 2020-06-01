(ns ^:figwheel-hooks claby.ux.mini
  "Minimal ux for Lapyrinthe aimed at AI training."
  (:require
   [clojure.test.check]
   [clojure.test.check.properties]
   [cljs.spec.alpha :as s]
   [cljs.spec.gen.alpha :as gen]
   [claby.ux.base :refer [mount-app-element]]))

(mount-app-element)

