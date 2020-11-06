(defproject claby "0.1.0-SNAPSHOT"
  :description "A simple game of eating fruits in a maze, avoiding
  unpasteurized cheese and moving enemies. Implementations of various
  artificial algorithms to play this game (`players`)."
  :url "https://github.com/sittingbull/mzero"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/test.check "1.0.0"]
                 [org.clojure/data.generators "1.0.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.1.0"]]

  :jvm-opts ["-Xss1g"]
  :source-paths ["src" "scripts"]
  :main claby.ai.main)
