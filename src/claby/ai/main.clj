(ns claby.ai.main
  "Main thread for AI game. Start game with the `run` function, see
  below for CLI Options."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.cli :as ctc]
            [clojure.tools.logging :as log]
            [claby.ai.world :as aiw]
            [claby.game.state :as gs]
            [claby.ai.player :as aip]
            [claby.game.generation :as gg]
            [claby.ai.exhaustive-player :refer [exhaustive-player]])
  (:gen-class))

(def cli-options
  [["-g" "--game-step-duration GST"
    "Time interval (ms) between each game step (see claby.ai.world)."
    :default 25
    :parse-fn #(Integer/parseInt %)]
   ["-p" "--player-step-duration PST"
    "Time interval (ms) between each move request from player."
    :default 50
    :parse-fn #(Integer/parseInt %)]
   ["-s" "--board-size SIZE"
    "Board size for the game"
    :default 12
    :parse-fn #(Integer/parseInt %)]
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

(defn run
  "Runs a game with `initial-data` matching world-state spec (see world.clj).
  Opts is a map containing `:player-step-duration` and `:game-step-duration`"
  ([opts initial-state]
   (log/info "Running game with the following options:\n" opts)

   (let [world-state (atom nil)]

     (aiw/initialize-game world-state initial-state opts)
     
     ;; setup interactive mode if requested    
     (when (opts :interactive)
       (let [interactivity-atom (atom :pause)]
         (setup-interactivity world-state
                              interactivity-atom
                              (opts :number-of-steps))
         (future (process-user-input interactivity-atom))))
     
     ;; run game and player threads 
     (let [game-result
           (future (aiw/run-until-end world-state opts))]
       (future (aip/play-until-end world-state
                                   (atom (exhaustive-player @world-state))
                                   (opts :player-step-duration)))
       
       ;; return game thread result
       @game-result)))
   
  ([opts]
   (run opts (gg/create-nice-game
              (opts :board-size)
              {::gg/density-map {:fruit 5}}))))

(defn -main [& args]
  (let [opts (ctc/parse-opts args cli-options)]
    (cond
      (-> opts :options :help)
      (println (opts :summary))
      
      (opts :errors)
      (println (str/join "\n" (opts :error)))
      
      :else
      ((run (opts :options))
       (shutdown-agents)))))
