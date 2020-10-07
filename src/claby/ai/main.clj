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
            [claby.game.state :as gs]
            [claby.ai.player :as aip]
            [claby.ai.game-runner :as gr]
            [claby.game.generation :as gg]
            [claby.ai.players.random])
  (:gen-class))

(def cli-options
  [["-s" "--board-size SIZE"
    "Board size for the game"
    :default 12
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 4 % 100) "Must be an int between 4 and 100"]]
   ["-i" "--interactive"
    "Run the game in interactive mode (see README.md)"]
   ["-n" "--number-of-steps STEPS"
    "Number of steps in-between interaction. Only used in interactive mode."
    :default 50
    :parse-fn #(Integer/parseInt %)]
   ["-l" "--logging-steps STEPS"
    "Log the world state every STEPS steps. Only used in
    *non*-interactive mode. 0 means no logging during game."
    :default 0
    :parse-fn #(Integer/parseInt %)]
   ["-t" "--player PLAYER-TYPE"
    "Artificial player that will play the game. Arg should be the
    namespace in which the protocol is implemented, unqualified (it
    will be prefixed with claby.ai.players). E.g. \"random\" will
    target the RandomPlayer protocol implementation in
    claby.ai.players.random (it's a player moving at random)."
    :default claby.ai.players.random/map->RandomPlayer
    :parse-fn aip/load-player-constructor-by-name
    :validate [#(some? %)]]
   ["-r" "--game-runner GAME-RUNNER"
    "Game runner function to use. ATTOW, ClockedThreadsRunner,
    MonoThreadRunner or WatcherRunner (which breaks for board sizes > 10)"
    :default gr/->MonoThreadRunner
    :parse-fn #(resolve (symbol (str "claby.ai.game-runner/->" %)))
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

;;; Interactive mode setup
;;;;;;;;;;

(s/def ::interactive-command #{:quit :pause :step :run})

(defn- run-interactive-mode
  "Interactive mode will print current game data, and behave depending
  on user provided commands:
  
  - (q)uit will abort the game (done in the run function)
  - (Enter while running) pause will pause the game
  - (Enter while paused) step will run the next number-of-steps and pause
  - (r)un/(r)esume will proceed with running the game."
  [world-state interactivity-atom]
  (println (aiw/data->string world-state))
  (swap! interactivity-atom #(if (= % :step) :pause %))
  (while (= :pause @interactivity-atom)
    (Thread/sleep 100)))

(defn- setup-interactivity
  [world-state interactivity-atom number-of-steps]
  (add-watch world-state :setup-interactivity
             (fn [_ _ old-data {:as new-data, :keys [::aiw/game-step]}]
               (when (and (< (old-data ::aiw/game-step) game-step)
                          (= (mod game-step number-of-steps) 0))
                 (run-interactive-mode new-data interactivity-atom))))
  
  (add-watch interactivity-atom :abort-game-if-quit
             (fn [_ _ _ val]
               (if (= :quit val)
                 (swap! world-state
                        assoc-in [::gs/game-state ::gs/status] :over)))))

(s/fdef get-interactivity-value
  :args (s/cat :prev-val ::interactive-command :user-input string?)
  :ret ::interactive-command)

(defn- get-interactivity-value [prev-val user-input]
  (case user-input
    "q" :quit
    "r" :run
    "" (if (= prev-val :pause) :step :pause)
    prev-val))

(defn- process-user-input [interactivity-atom]
  (while (not= :quit @interactivity-atom)
    (swap! interactivity-atom get-interactivity-value (read-line))))

;;; Main game routine
;;;;;;

(defn- start-interactive-mode
  [world-state nb-steps]
  (let [interactivity-atom (atom :pause)]
    (setup-interactivity world-state interactivity-atom nb-steps)
    (future (process-user-input interactivity-atom))))

(defn run
  "Runs a game with `initial-state` matching world specs (see world.clj)."
  ([opts initial-game-state]
   (let [world-state (atom (aiw/get-initial-world-state initial-game-state))]
     ;; setup logging
     (.setLevel (li/get-logger log/*logger-factory* "") (opts :logging-level))
     (log/info "Running game with the following options:\n" opts)

     ;; setup interactivity if requested
     (when (opts :interactive)
       (start-interactive-mode world-state (opts :number-of-steps)))
     
     ;; runs the game
     (log/info "The game begins.\n" (aiw/data->string @world-state))
     (let [player-state
           (atom (aip/init-player ((opts :player) {}) @world-state))
           result
           (gr/run-game ((opts :game-runner) world-state player-state opts))]
       (log/info "The game ends.\n" (aiw/data->string  @world-state))
       result)))
   
  ([opts]
   (run opts (gg/create-nice-game (opts :board-size)
                                  {::gg/density-map {:fruit 5}}))))

(defn -main [& args]
  (let [opts (ctc/parse-opts args cli-options)]
    (cond
      (-> opts :options :help)
      (println (opts :summary))
      
      (some? (opts :errors))
      (println (str "There were error(s) in args parsing.\n"
                    (str/join "\n" (opts :errors))))

      :else
      (run (opts :options)))))
