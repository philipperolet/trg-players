(ns claby.ai.players.tree-exploration
  "Tree exploration player. Monte-carlo simulations are performed using a data
  structure called an `tree-node`, which has a `value`, a `frequency`,
  a `direction` and  a `children` map, mapping directions to tree-nodes.

  Frequency stores the number of times the node has been
  visited. Value stores the smallest number of steps to a fruit found
  so far from this node."
  (:require [claby.ai.player :as aip]
            [claby.game.events :as ge]
            [claby.game.state :as gs]
            [claby.game.board :as gb]
            [clojure.set :as cset]
            [clojure.spec.alpha :as s]
            [claby.utils :as u]
            [clojure.zip :as zip]))

(s/def ::value nat-int?)

(s/def ::frequency nat-int?)

(defn sum-children-frequencies [tree-node]
  (reduce + (map ::frequency (vals (::children tree-node)))))

(s/def ::tree-node
  (-> (s/keys :req [::frequency ::children]
              :opt [::value ::ge/direction])
      (s/and
       (fn [tn]
         (comment "A tree node either has no children, or its freq is
         the sum of freqs of its children")
         (or (empty? (::children tn))
          (= (::frequency tn)
             (sum-children-frequencies tn)))))))

(s/def ::children (s/map-of ::ge/direction ::tree-node :max-count 4 :distinct true))

(def default-nb-sims 200)

(defn- max-sim-size [game-state]
  (let [board-size (count (::gb/game-board game-state))]
    (* 2 board-size)))

(defn- worst-value
  "Value given to a node once the end of the simulation is reached and
  no fruit has been found."
  [game-state]
  (* 2 (max-sim-size game-state)))

(defn- min-child
  "Returns the child of tree-node with the minimum value."
  [tree-node]
  (apply min-key ::value (vals (::children tree-node))))



(defn- select-next
  [node-children]
  (if (< (count node-children) 4)
    ;; new node with direction not already in `node-children`
    {::ge/direction (-> ge/directions
                        (cset/difference (set (keys node-children)))
                        first)
     ::frequency 0
     ::children {}}
    ;; less visited child is selected
    (apply min-key ::frequency (vals node-children))))

(s/fdef tree-simulate
  :args (s/and (s/cat :game-state ::gs/game-state
                      :sim-size nat-int?
                      :tree-node ::tree-node))
  :ret ::tree-node)

(defmulti tree-simulate
  "Simulates `sim-size` steps of a game from `game-state`, with
  exploration data at current step stored in `tree-node`.
  Returns tree node with updated data."
  (fn [_ _ n] (if (vector? n) :zipper :basic)))

(defmethod tree-simulate :basic
  [game-state sim-size tree-node]
  (let [next-state (ge/move-player game-state (::ge/direction tree-node))]
    (-> (cond
          (< (::gs/score game-state) (::gs/score next-state))
          (assoc tree-node ::value 0)

          (or (zero? sim-size) (not (= (::gs/status next-state) :active)))
          (assoc tree-node ::value (worst-value game-state))

          :else
          (let [next-node (select-next (-> tree-node ::children))]
            (-> tree-node
                (assoc-in [::children (::ge/direction next-node)]
                          (tree-simulate next-state (dec sim-size) next-node))
                (#(assoc % ::value (inc (::value (min-child %))))))))
        (update ::frequency inc))))

(defn- simulate-games
  [game-state node nb-sims]
  (let [simulate-once-from-node
        (partial tree-simulate game-state (max-sim-size game-state))]    
    (->> node
         (iterate simulate-once-from-node)
         (#(nth % nb-sims)))))

(defn node-path
  "Return `tree-node` with its children only (not its children's
  children, etc). If `directions` are given, then shows the node, the
  child from the 1st provided direction, the child of this child
  provided by the second direction, etc."
  [tree-node & directions]
  (if (empty? directions)
    ;; remove "children" fields from the node's children
    (update tree-node ::children (partial u/map-map #(dissoc % ::children)))
    
    (-> tree-node
        (update ::children #(select-keys % (list (first directions))))
        (update-in [::children (first directions)]
                   #(apply node-path % (rest directions))))))

(defn- make-node
  "make-node fn for `::tree-node` as specified in `clojure.zip/zipper`"
  [node children]
  (->> children
       (reduce #(assoc %1 (::ge/direction %2) %2) {})
       (assoc node ::children)))

(defprotocol TreeExplorer
  (create-node [this world direction]))

(defn- compute-root-node [this world]
  (let [simulate-on-each-direction
        #(simulate-games
          (::gs/game-state world)
          (create-node this world %)
          (/ (-> this :nb-sims) (count ge/directions)))]
    (->> ge/directions
         (pmap simulate-on-each-direction)
         (make-node {}))))

(s/def ::options (s/map-of #{:nb-sims} pos-int?))

(defn- init-player
  [this opts]
  (assert (s/valid? ::options opts))
  (assoc this :nb-sims (-> opts (:nb-sims default-nb-sims))))

(defn- update-player
  [this world]
  (-> this
        (assoc :root-node (compute-root-node this world))
        (#(assoc % :next-movement (->  % :root-node min-child ::ge/direction)))))

(defrecord TreeExplorationPlayer [nb-sims]
  aip/Player
  (init-player [this opts world]
    (init-player this opts))
  
  (update-player [this world]
    (update-player this world)))

(defrecord FakeProtocol [])

(extend-protocol TreeExplorer
  TreeExplorationPlayer
  (create-node [this world direction]
    {::frequency 0 ::children {} ::ge/direction direction})
  FakeProtocol
  (create-node [this world direction]
    (zip/zipper (comp map? ::children)
                (comp vals ::children)
                make-node
                {::frequency 0                 
                 ::ge/direction direction
                 ::children {}})))
