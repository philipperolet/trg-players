(ns claby.ai.game
  "Game thread with main game loop, responsible for setting up/ending
  the game, updating game state, providing player senses, executing
  player and enemies movement requests.

  The game runs in discretized time, with a specified `game-step-duration`.
  At every `game-step`, the game checks whether movements are requested via
  `requested-movements`, executes them and updates the `game-state`.

  An execution that takes longer than the duration of a step
  will be considered an exception."
  
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.game.state :as gs]
            [claby.game.events :as ge]
            [claby.game.generation :as gg]
            [claby.utils :as u]
            [clojure.tools.logging :as log]))

;;; Game data spec & function
;;;;;;;

;; Time interval (ms) between each game step (see claby.ai.game)
(s/def ::game-step-duration (s/int-in 1 1000))

(s/def ::game-step int?)

(s/def ::requested-movements (s/map-of ::ge/being ::ge/direction))

(s/def ::full-state
  (-> (s/keys :req [::gs/game-state ::game-step ::requested-movements])
      
      (s/and (fn [{:keys [::requested-movements]
                   {:keys [::gs/enemy-positions]} ::gs/game-state}]
               (comment "for all movements,  enemy index < enemy count")
               (every? #(or (= % :player) (< % (count enemy-positions)))
                       (keys requested-movements))))))
                       

(defn create-game-with-state [game-state]
  {::gs/game-state game-state
   ::game-step 0
   ::requested-movements {}})


(defn data->string
  "Converts game data to nice string"
  [{:keys [::gs/game-state ::game-step], :as full-state}]
  (str (format "Step %d\n" game-step)
       (gs/state->string game-state)))

;;; Running the game
;;;;;;

(s/fdef run-step
  :args (-> (s/cat :full-state ::full-state)
            (s/and #(= (-> % :full-state ::gs/game-state ::gs/status) :active)))
  :ret ::full-state
  :fn (s/and
       (fn [{{:keys [full-state]} :args, :keys [ret]}]
         (comment "Step increases")
         (= (::game-step ret) (inc (::game-step full-state))))
       (fn [{:keys [:ret]}]
         (comment "Movements cleared")
         (empty? (::requested-movements ret)))))

(defn run-step
  "Runs a step of the game : execute movements, clear requested-movements,
  increase step."
  [{:as full-state, :keys [::requested-movements]}]
  (-> full-state
      
      ;; execute movements, stop if ever game ends
      (update 
       ::gs/game-state
       (partial u/reduce-until #(not= (::gs/status %) :active) ge/move-being)
       requested-movements)
      
      (assoc ::requested-movements {})
      (update ::game-step inc)))

(defn active?
  [full-state]
  (= :active (-> full-state ::gs/game-state ::gs/status)))

(defn run-game
  "Main game loop."
  [full-state-atom game-step-duration]
  {:pre [(s/valid? ::game-step-duration game-step-duration)]}
  (log/info "The game begins.\n" (data->string @full-state-atom))
  
  (while (active? @full-state-atom)
    (Thread/sleep game-step-duration)
    (swap! full-state-atom run-step))

  (log/info "The game ends.\n" (data->string  @full-state-atom))
  @full-state-atom)
