(ns claby.ai.world
  "World thread, with main game loop, responsible for setting up/ending
  the game, updating game state, providing player senses, executing
  player and enemies movement requests.

  The game runs in discretized time, with a specified
  `game-step-duration`.  At every `game-step`,`run-individual-step`
  checks whether movements are requested via `requested-movements`,
  executes them and updates the `game-state`.

  An execution that takes longer or equal than the duration of a step
  will add a `misstep` to the state of the game--thus taking the exact
  step duration time to execute is still considered a misstep. This is
  handled by `update-timing-data`

  If `too_many_missteps` are made, an exception will be thrown."
  
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.game.state :as gs]
            [claby.game.events :as ge]
            [claby.utils :as u]
            [clojure.tools.logging :as log]))

;;; Full game state spec & helpers
;;;;;;;


;; Time interval (ms) between each game step
(def max-game-step-duration 1000)

(s/def ::game-step-duration (s/int-in 1 max-game-step-duration))

(s/def ::game-step nat-int?)

;; timestamp in ms of current step start
(s/def ::step-timestamp nat-int?)

;; time to wait = game step duration - step execution time (obtained by comparing step timestamps before and after running the step)
;; or 0 if step execution time exceeded game step duration
(s/def ::time-to-wait (s/int-in 0 max-game-step-duration))

;; number of steps that were not performed fast enough (i.e. took more than game-step-duration
(s/def ::missteps nat-int?)

(s/def ::requested-movements (s/map-of ::ge/being ::ge/direction))

(defn- full-state-predicate-matcher
  "Generator helper to match ::full-state constraints"
  [full-state]
  (-> full-state
      (update ::missteps min (full-state ::game-step))
      (assoc ::requested-movements
             (u/filter-keys
              #(or (= % :player)
                   (< % (count (get-in full-state
                                       [::gs/game-state ::gs/enemy-positions]))))
              (full-state ::requested-movements)))))

(def full-state-keys-spec
  (s/keys :req [::gs/game-state
                ::game-step
                ::requested-movements
                ::step-timestamp 
                ::time-to-wait
                ::missteps]))

(s/def ::full-state
  (-> full-state-keys-spec
      (s/and (fn [{:keys [::requested-movements]
                   {:keys [::gs/enemy-positions]} ::gs/game-state}]
               (comment "for all movements,  enemy index < enemy count")
               (every? #(or (= % :player) (< % (count enemy-positions)))
                       (keys requested-movements)))
             (fn [{:keys [::missteps ::game-step]}]
               (comment "no more missteps than actual game steps")
               (<= missteps game-step)))
      (s/with-gen #(gen/fmap
                    full-state-predicate-matcher
                    (s/gen full-state-keys-spec)))))


(defn data->string
  "Converts game data to nice string"
  [{:keys [::gs/game-state ::game-step ::step-timestamp ::time-to-wait]}]
  (str (format "Step %d\nTimestamp (mod 1 000 000) %d\nTime-to-wait %d\n"
               game-step
               (mod step-timestamp 1000000)
               time-to-wait)
       (gs/state->string game-state)))

(defn active?
  [full-state]
  (= :active (-> full-state ::gs/game-state ::gs/status)))

;;; Game initialization
;;;;;;

(defn get-initial-full-state
  ([game-state initial-timestamp]
   {::gs/game-state (assoc game-state ::gs/status :active)
    ::game-step 0
    ::requested-movements {}
    ::time-to-wait 0
    ::missteps 0
    ::step-timestamp initial-timestamp})
  ([game-state] (get-initial-full-state game-state (System/currentTimeMillis))))

(defn initialize-game
  "Sets everything up for the game to start (arg check, state reset, log)."
  [state-atom initial-state {:as opts, :keys [game-step-duration player-step-duration]}]
  {:pre [(s/valid? ::game-step-duration game-step-duration)
         (s/valid? ::game-step-duration player-step-duration)]}  
  (reset! state-atom (get-initial-full-state initial-state))
  (log/info "The game begins.\n" (data->string @state-atom)))

;;; Game execution
;;;;;;

(s/fdef compute-new-state
  :args (-> (s/cat :full-state ::full-state)
            (s/and
             #(= (-> % :full-state ::gs/game-state ::gs/status) :active)))
  :ret ::full-state
  :fn (s/and
       (fn [{{:keys [full-state]} :args, :keys [ret]}]
         (comment "Step increases")
         (= (::game-step ret) (inc (::game-step full-state))))
       (fn [{:keys [:ret]}]
         (comment "Movements cleared")
         (empty? (::requested-movements ret)))))

(defn update-timing-data
  "Timing is handled as follows:
  - at game step beginning, state is updated with the step timestamp
  - i"
  [full-state new-timestamp game-step-duration]
  (let [step-execution-time (- new-timestamp (full-state ::step-timestamp))
        time-to-wait (max (- game-step-duration step-execution-time) 0)]
    (-> full-state
        (assoc ::time-to-wait time-to-wait)
        (update ::missteps #(if (zero? time-to-wait) (inc %) %)))))

(defn compute-new-state
  "Computes the new state derived from running a step of the
  game. Executes movements until none is left or game is over."
  [{:as full-state, :keys [::requested-movements]}]
  (-> full-state
      (update ::gs/game-state
              (partial u/reduce-until #(not= (::gs/status %) :active) ge/move-being)
              requested-movements)
      (assoc ::requested-movements {})
      (update ::game-step inc)))

(defn run-individual-step
  "Runs a step. The timing is handled as follows:
  - first compare time since last step's beginning with game step duration;
  - if bigger, count it as a misstep;
  - wait for the remaining amount of time (0 if time was bigger);
  - update the state with the new state's starting time
  - compute the new state"
  [full-state-atom game-step-duration]
  (swap! full-state-atom update-timing-data
         (System/currentTimeMillis) game-step-duration)
  (Thread/sleep (@full-state-atom ::time-to-wait))
  (swap! full-state-atom assoc ::step-timestamp (System/currentTimeMillis))
  (swap! full-state-atom compute-new-state)
  (log/info (data->string @full-state-atom)))

(defn run-until-end
  "Main game loop."
  [full-state-atom game-step-duration]
  (while (active? @full-state-atom)
    (run-individual-step full-state-atom game-step-duration))
  (log/info "The game ends.\n" (data->string  @full-state-atom))
  @full-state-atom)
