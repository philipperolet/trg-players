;; Code to be run via cat | lein repl
(->> (run-games {:layer-dims [1024 1024]} 500 26)
     :game-measurements
     (map :fruit-move-ratio)
     (take-last 20)
     (apply +)
     (* 0.05)
     println)
