(defproject mzero "00.1.3-alpha"
  :description "Artificial player for mzero-game, who will hopefully
  become intelligent."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.7.1"

  ;; DEPENDENCY WARNING : if changing dependencies, make sure
  ;; that both neanderthal & clojurecuda rely on the same version
  ;; of the CUDA toolkit (otherwise sh*t may fail)
  ;; ATTOW neanderthal 43.2 & clojurecuda 14 use cuda toolkit 11.4.1
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/test.check "1.1.0"]
                 [org.clojure/data.generators "1.0.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.1.0"]
		 [org.clojure/tools.namespace "1.1.0"]
                 [mzero-game "0.2.6"]
                 [uncomplicate/neanderthal "0.43.2"]
                 [uncomplicate/commons "0.12.3"]
                 [incanter/incanter-core "1.9.3"]
                 [incanter/incanter-charts "1.9.3"]]

  :jvm-opts
  ["-Xss256m" "-Xms2G" "-Xmx8G"
   "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED"
   "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/jul-factory"]
  :source-paths ["src" "xps"]
  :main mzero.ai.train
  :injections [(require '[mzero.utils.utils :as u])
               (require '[mzero.ai.debug :refer :all])]
  :test-selectors {:default (fn [m & _] (not (or (:deprecated m) (:skip m))))}
  :repl-options {:init (do (require '[clojure.tools.namespace.repl :refer [refresh]])
  		       	   (set! *print-length* 10) (set! *print-level* 5))}
  :profiles {:cuda
             {:dependencies [[uncomplicate/clojurecuda "0.14.0"]]
              :source-paths ["src-gpu/cuda"]}
             :opencl
             {:dependencies [[uncomplicate/clojurecl "0.15.1"]]
              :source-paths ["src-gpu/opencl"]}
             :repl {:plugins [[cider/cider-nrepl "0.25.2"]]}})
