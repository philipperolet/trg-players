(ns claby.ai.world
  "Module responsible for running the world, by:
  - listening to movement requests (on `requested-movements`);
  - updating the `world-state` according to the movement requests;
  - updating the world's timestamp & step number every time its state changes
    (ATTOW only via requested-movements).

  Regarding the first element, movement requests can be made by the
  player as well as by enemies.

  Three constraints are enforced :
  
  1. **thread-safe consistency of movement**: if an external thread sees that
  `requested-movements` is empty, it means that game state has already
  been updated. Conversely, if `requested-movements` is not empty,
  `game-state` has *not* been updated with those movements;

  2. **thread-safe consistency of history**: world steps and
  timestamps should *always* be increased atomically whenever
  `world-state` changes, it should not be possible to observe
  `world-state` in which a change has been made (e.g. to
  `requested-movement`) but timestamp/step have not yet been updated

  3. **timeliness** of the game, meaning that executing requested
  movements should not take more than 1ms. The program will not halt
  at the first delay over 1ms, for stability. However, it will throw
  an exception if delays happen too much.

  The 2nd constraint is ensured through TODO (specs on state changers,
  optionally a watcher *checking* the constraint). It is not ensured
  by a watcher actually *updating* timestamp & step, because it could
  lead to inconsistent states, since the occurence of a state change
  and the execution of the watcher function are not atomically
  performed."
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

(defn- world-state-predicate-matcher
  "Generator helper to match ::world-state constraints"
  [world-state]
  (-> world-state
      (update ::missteps min (world-state ::game-step))
      (assoc ::requested-movements
             (u/filter-keys
              #(or (= % :player)
                   (< % (count (get-in world-state
                                       [::gs/game-state ::gs/enemy-positions]))))
              (world-state ::requested-movements)))))

(def world-state-keys-spec
  (s/keys :req [::gs/game-state
                ::game-step
                ::requested-movements
                ::step-timestamp 
                ::time-to-wait
                ::missteps]))

(s/def ::world-state
  (-> world-state-keys-spec
      (s/and (fn [{:keys [::requested-movements]
                   {:keys [::gs/enemy-positions]} ::gs/game-state}]
               (comment "for all movements,  enemy index < enemy count")
               (every? #(or (= % :player) (< % (count enemy-positions)))
                       (keys requested-movements)))
             (fn [{:keys [::missteps ::game-step]}]
               (comment "no more missteps than actual game steps")
               (<= missteps game-step)))
      (s/with-gen #(gen/fmap
                    world-state-predicate-matcher
                    (s/gen world-state-keys-spec)))))


(defn data->string
  "Converts game data to nice string"
  [{:keys [::gs/game-state ::game-step ::step-timestamp ::time-to-wait]}]
  (str (format "Step %d\nTimestamp (mod 1 000 000) %d\nTime-to-wait %d\n"
               game-step
               (mod step-timestamp 1000000)
               time-to-wait)
       (gs/state->string game-state)))

(defn active?
  [world-state]
  (= :active (-> world-state ::gs/game-state ::gs/status)))

;;; Game initialization
;;;;;;

(defn get-initial-world-state
  ([game-state initial-timestamp]
   {::gs/game-state (assoc game-state ::gs/status :active)
    ::game-step 0
    ::requested-movements {}
    ::time-to-wait 0
    ::missteps 0
    ::step-timestamp initial-timestamp})
  ([game-state] (get-initial-world-state game-state (System/currentTimeMillis))))

(defn initialize-game
  "Sets everything up for the game to start (arg check, state reset, log)."
  [world-state-atom initial-game-state {:as opts, :keys [player-step-duration]}]
  {:pre [(s/valid? ::game-step-duration player-step-duration)]}  
  (reset! world-state-atom (get-initial-world-state initial-game-state))
  (log/info "The game begins.\n" (data->string @world-state-atom)))

;;; Game execution
;;;;;;

(s/fdef compute-new-state
  :args (-> (s/cat :world-state ::world-state
                   :step-timestamp ::step-timestamp)
            (s/and
             #(= (-> % :world-state ::gs/game-state ::gs/status) :active)
             (fn [{:keys [step-timestamp world-state]}]
               (comment "New timestamp must be bigger than previous one")
               (>= step-timestamp (world-state ::step-timestamp)))))
  :ret ::world-state
  :fn (s/and
       (fn [{{:keys [world-state]} :args, :keys [ret]}]
         (comment "Step increases")
         (= (::game-step ret) (inc (::game-step world-state))))
       (fn [{:keys [:ret]}]
         (comment "Movements cleared")
         (empty? (::requested-movements ret)))))

(defn update-timing-data
  "Timing is handled as follows:
  - at game step beginning, state is updated with the step timestamp
  - i"
  [world-state new-timestamp game-step-duration]
  (let [step-execution-time (- new-timestamp (world-state ::step-timestamp))
        time-to-wait (max (- game-step-duration step-execution-time) 0)]
    (-> world-state
        (assoc ::time-to-wait time-to-wait)
        (update ::missteps #(if (zero? time-to-wait) (inc %) %)))))

(defn compute-new-state
  "Computes the new state derived from running a step of the
  game. Executes movements until none is left or game is over."
  [{:as world-state, :keys [::requested-movements]} step-timestamp]
  (-> world-state
      (update ::gs/game-state
              (partial u/reduce-until #(not= (::gs/status %) :active) ge/move-being)
              requested-movements)
      (assoc ::requested-movements {})
      (update ::game-step inc)
      (assoc ::step-timestamp step-timestamp)))

(defn- setup-update-world-on-movement-request [world-state-atom]
  "Sets up listening of movement requests, so that every time a
  movement is requested the world state is updated accordingly.

  If `requested-movements` is not empty, a state change is
  triggered--which will in turn call the watch again, thus the
  necessity to check in the watch whether `requested-movements` is
  empty, in addition to the check performed in the (atomic) execution
  of movement.
  
  It is possible that between the watch trigger and the watch function
  execution, other movements are requested, thus triggering another
  watch call with a different . It is not an issue since when the
  first watch call is executed, it swaps the current atom value, not
  the one at watch trigger (stored in new-state)."
  (add-watch world-state-atom
             :update-on-movement-request
             (fn [_ atom _ new-state]
               (if (not-empty (new-state ::requested-movements))
                 (swap! atom compute-new-state (System/currentTimeMillis))))))

(defn run-individual-step
  "Runs a step. The timing is handled as follows:
  - first compare time since last step's beginning with game step duration;
  - if bigger, count it as a misstep;
  - wait for the remaining amount of time (0 if time was bigger);
  - update the state with the new state's starting time
  - compute the new state"
  [world-state-atom game-step-duration]
  (swap! world-state-atom update-timing-data
         (System/currentTimeMillis) game-step-duration)
  (Thread/sleep (@world-state-atom ::time-to-wait))
  (swap! world-state-atom assoc ::step-timestamp (System/currentTimeMillis))
  (swap! world-state-atom compute-new-state (System/currentTimeMillis))
  (log/info (data->string @world-state-atom)))

(defn run-until-end
  "Main game loop."
  [world-state-atom game-step-duration]
  (while (active? @world-state-atom)
    (run-individual-step world-state-atom game-step-duration))
  (log/info "The game ends.\n" (data->string  @world-state-atom))
  @world-state-atom)
