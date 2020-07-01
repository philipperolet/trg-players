(ns claby.ai.main
  "Main thread for AI game. Start game with the `run` function, see
  below for CLI Options."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [clojure.tools.cli :as ctc]
            [clojure.tools.logging :as log]
            [claby.ai.game :as aig]
            [claby.game.state :as gs]
            [claby.ai.player :as aip]
            [claby.game.generation :as gg])
  (:gen-class))

(def cli-options
  [["-g" "--game-step-duration GST"
    "Time interval (ms) between each game step (see claby.ai.game)."
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
  [full-state interactivity-atom]
  (println (aig/data->string full-state))
  (swap! interactivity-atom #(if (= % :step) :pause %))
  (while (= :pause @interactivity-atom)
    (Thread/sleep 100)))

(defn- setup-interactivity
  [full-state-atom interactivity-atom number-of-steps]
  (add-watch full-state-atom :setup-interactivity
             (fn [_ _ old-data {:as new-data, :keys [::aig/game-step]}]
               (when (and (< (old-data ::aig/game-step) game-step)
                          (= (mod game-step number-of-steps) 0))
                 (run-interactive-mode new-data interactivity-atom))))
  
  (add-watch interactivity-atom :abort-game-if-quit
             (fn [_ _ _ val]
               (if (= :quit val)
                 (swap! full-state-atom
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
  "Runs a game with `initial-data` matching full-state spec (see game.clj).
  Opts is a map containing `:player-step-duration` and `:game-step-duration`"
  ([opts initial-state]
   (log/info "Running game with the following options:\n" opts)

   ;; setup game
   (let [full-state (atom (aig/create-game-with-state initial-state))]
     (swap! full-state assoc-in [::gs/game-state ::gs/status] :active)
     
     ;; run game and player threads 
     (let [game-result (future (aig/run-game full-state (opts :game-step-duration)))]
       (future (aip/run-player full-state (opts :player-step-duration)))

       ;; setup interactive mode if needed
       (when (opts :interactive)
         (let [interactivity-atom (atom :pause)]
           (setup-interactivity full-state interactivity-atom (opts :number-of-steps))
           (future (process-user-input interactivity-atom))))
       
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
