(ns claby.ai.main
  "Main thread for AI game. Start game with the `run` function, see
  below for CLI Options. There are multiple available implementations
  of game running, modeled by the `GameRunner` protocol.

  Most importantly, various implementations of articificial players
  can be specified via the `Player` protocol in `claby.ai.player`."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.cli :as ctc]
            [clojure.tools.logging :as log]
            [clojure.tools.logging.impl :as li]
            [claby.ai.world :as aiw]
            [claby.ai.player :as aip]
            [claby.ai.game-runner :as gr]
            [claby.game.generation :as gg])
  (:gen-class))

(defn- parse-game-runner [runner-name]
  (if (= runner-name "ClockedThreadsRunner")
    (do (require 'claby.ai.clocked-threads-runner)
        (resolve 'claby.ai.clocked-threads-runner/->ClockedThreadsRunner))
    (resolve (symbol (str "claby.ai.game-runner/->" runner-name)))))

(def cli-options
  [["-s" "--board-size SIZE"
    "Board size for the game"
    :default 12
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 4 % 100) "Must be an int between 4 and 100"]]
   ["-i" "--interactive"
    "Run the game in interactive mode (see README.md)"]
   ["-n" "--number-of-steps STEPS"
    "Number of steps that the game should run. If not specified, the
    game runs until it ends. In non-interactive mode, execution
    terminates after STEPS steps. If in interactive mode, user is
    asked for action after STEPS steps."
    :parse-fn #(Integer/parseInt %)]
   ["-l" "--logging-steps STEPS"
    "Log the world state every STEPS steps. Only used in
    *non*-interactive mode. 0 means no logging during game."
    :default 0
    :parse-fn #(Integer/parseInt %)
    :validate [int?]]
   ["-t" "--player-type PLAYER-TYPE"
    "Artificial player that will play the game. Arg should be the
    namespace in which the protocol is implemented, unqualified (it
    will be prefixed with claby.ai.players). E.g. \"random\" will
    target the RandomPlayer protocol implementation in
    claby.ai.players.random (it's a player moving at random)."
    :default "random"]
   ["-o" "--player-opts PLAYER-OPTIONS"
    "Map of player options, specific to each player type."
    :default {}
    :parse-fn read-string
    :validate [map?]]
   ["-r" "--game-runner GAME-RUNNER"
    "Game runner function to use. ATTOW, ClockedThreadsRunner,
    MonoThreadRunner or WatcherRunner (which breaks for board sizes > 10)"
    :default gr/->MonoThreadRunner
    :parse-fn parse-game-runner
    :validate [#(some? %)]]
   ["-v" "--logging-level LEVEL"
    "Verbosity, specified as a logging level"
    :default java.util.logging.Level/INFO
    :parse-fn #(java.util.logging.Level/parse %)
    :validate [#(some? %)]]
   ["-g" "--game-step-duration GST"
    "Time finterval (ms) between each game step, used only by
    ClockedThreadsRunner"
    :default 5
    :parse-fn #(Integer/parseInt %)]
   ["-p" "--player-step-duration PST"
    "Time interval (ms) between each move request from player, used
    only by ClockedThreadsRunner"
    :default 5
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]])

(defn- parse-arg-string
  "Convenience function to get the args map from an arg string"
  [arg-string & format-vars]
  (->> (apply format arg-string format-vars)
       (#(str/split % #"'"))
       (map-indexed #(if (even? %1) (str/split %2 #" ") (vector %2)))
       (apply concat)))

(defn parse-run-args
  [args & format-vars]
  (let [parsed-data
        (-> (apply parse-arg-string args format-vars)
            (ctc/parse-opts cli-options))]
    (if (some? (parsed-data :errors))
      (throw (java.lang.IllegalArgumentException.
              (str "There were error(s) in args parsing.\n"
                   (str/join "\n" (parsed-data :errors)))))
      (:options parsed-data))))

;;; Interactive mode setup
;;;;;;;;;;

(s/def ::interactive-command #{:quit :pause :step :run})

(s/fdef get-interactivity-value
  :args (s/cat :user-input string?)
  :ret ::interactive-command)

(defn- get-interactivity-value [user-input]
  (case user-input
    "q" :quit
    "r" :run
    "" :step))

(defn- run-game-interactively
  "Interactive mode will print current game data, and behave depending
  on user provided commands:
  
  - (q)uit will abort the game (done in the run function)
  - (Enter while running) will pause the game
  - (Enter while paused) will run the next number-of-steps and pause
  - (r)un/(r)esume will proceed with running the game."
  [world-state player-state opts]
  (loop [last-user-input :step]
    (gr/run-game ((opts :game-runner) world-state player-state opts))
    (log/info "Current world state:\n" (aiw/data->string @world-state))
    (cond
      (not (aiw/active? @world-state))
      nil
      
      (and (= last-user-input :run) (not (.ready *in*)))
      (recur :run)

      :else
      (let [user-input (get-interactivity-value (read-line))]
        (if (= user-input :quit)
          nil
          (recur user-input))))))

;;; Main game routine
;;;;;;
(defn run
  "Run a game given initial `world` & `player` states and game `opts`."
  ([opts world player]
   (let [world-state (atom world) player-state (atom player)]
     ;; setup logging
     (.setLevel (li/get-logger log/*logger-factory* "") (opts :logging-level))
     (log/info "Running game with the following options:\n" opts)

     ;; runs the game
     (log/info "Starting world state:\n" (aiw/data->string @world-state))
     (if (-> opts :interactive)
       (run-game-interactively world-state player-state opts)
       (gr/run-game ((opts :game-runner) world-state player-state opts)))
     (log/info "Ending world state:\n" (aiw/data->string  @world-state))
     {:world @world-state :player @player-state}))
  
  ([opts world]
   (run opts world
     (aip/load-player (opts :player-type) (opts :player-opts) world)))
  
  ([opts]
   (run opts
     (aiw/get-initial-world-state
      (gg/create-nice-game (opts :board-size) {::gg/density-map {:fruit 5}})))))

(defn -main [& args]
  (let [opts (parse-run-args (parse-arg-string args))]
    (if (some? (-> opts :help))
      (println (opts :summary))
      (run opts))))

(def curr-game (atom {:player nil :world nil :opts nil}))

(defn go [str-args & inits]
  (let [opts (parse-run-args str-args)]
    (reset! curr-game (apply run opts inits))
    (swap! curr-game assoc :opts opts)))

(defn n [& steps]
  (let [opts (assoc (:opts @curr-game) :number-of-steps (or (first steps) 1))]
    (swap! curr-game
           #(-> ((-> opts :game-runner) (-> % :world) (-> % :player) opts)
                gr/run-game
                (assoc :opts opts)))))
