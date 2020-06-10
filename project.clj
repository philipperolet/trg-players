(defproject claby "0.1.0-SNAPSHOT"
  :description "A simple game of eating fruits in a maze and avoiding
enemies, with 6 levels to clear. The game can be played by humans
in a browser (**Lapyrinthe**), or by computers using CLI (**AI game**)."
  :url "https://github.com/sittingbull/claby"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.758"]
                 [org.clojure/test.check "1.0.0"]
                 [reagent "0.10.0"]]

  :source-paths ["src"]
  :main claby.ai.main
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build-lapy" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:build-mini" ["trampoline" "run" "-m" "figwheel.main" "-b" "mini" "-r"]
            "fig:prod"   ["run" "-m" "figwheel.main" "-O" "none" "-bo" "mini"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "claby.test-runner"]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.4"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   }})
