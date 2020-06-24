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
    :default 50
    :parse-fn #(Integer/parseInt %)]
   ["-p" "--player-step-duration PST"
    "Time interval (ms) between each move request from player."
    :default 100
    :parse-fn #(Integer/parseInt %)]
   ["-s" "--board-size SIZE"
    "Board size for the game"
    :default 12
    :parse-fn #(Integer/parseInt %)]
   ["-i" "--interactive"
    "Run the game in interactive mode (see README.md)"]
   ["-n" "--number-of-steps STEPS"
    "Number of steps in-between interaction. Only used in interactive mode."
    :default 100
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]])

(defn- do-interactive-actions [game-data]
  (println (aig/data->string game-data)))

(defn- setup-interactivity [game-data-atom number-of-steps]
  (add-watch game-data-atom :setup-interactivity
             (fn [_ _ old-data {:as new-data, :keys [::aig/game-step]}]
               (when (and (< (old-data ::aig/game-step) game-step)
                          (= (mod game-step number-of-steps) 0))
                 (do-interactive-actions new-data)))))

(defn run
  "Runs a game with `initial-data` matching game-data spec (see game.clj).
  Opts is a map containing `:player-step-duration` and `:game-step-duration`"
  ([opts initial-state]
   (log/info "Running game with the following options:\n" opts)

   ;; setup game
   (let [game-data (atom (aig/create-game-with-state initial-state))]
     (swap! game-data assoc-in [::gs/game-state ::gs/status] :active)
     (if (opts :interactive)
       (setup-interactivity game-data (opts :number-of-steps)))
     
     ;; run game & player threads & return game thread result
     (let [game-result (future (aig/run-game game-data (opts :game-step-duration)))]
       (future (aip/run-player game-data (opts :player-step-duration)))
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
