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

(defn- dispatch-zipper
  "Dispatch functions for multimethods targeting tree nodes/loc"
  [node-or-loc & _]
  (if (vector? node-or-loc) :zipper :basic))

(defmulti children dispatch-zipper)
(defmethod children :basic [node] (vals (::children node)))
(defmethod children :zipper [loc] (zip/children loc))

(defmulti node dispatch-zipper)
(defmethod node :basic [node] node)
(defmethod node :zipper [loc] (zip/node loc))

(defmulti append-child dispatch-zipper)
(defmethod append-child :basic [node item]
  (assoc-in node [::children (::ge/direction item)] item))
(defmethod append-child :zipper [loc item]
  (zip/append-child loc item))

(s/def ::value nat-int?)

(s/def ::frequency nat-int?)

(defn sum-children-frequencies [tree-node]
  (reduce + (map ::frequency (children tree-node))))

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

(defn- make-node
  "make-node fn for `::tree-node` as specified in `clojure.zip/zipper`"
  [node children]
  (->> children
       (reduce #(assoc %1 (::ge/direction %2) %2) {})
       (assoc node ::children)))

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
  ([tree-node sort-key]
   (apply min-key sort-key (children tree-node))))

(defn- update-children
  "Adds a child if not all have been generated yet."
  [node-or-loc]
  (cond-> node-or-loc
    (< (count (children node-or-loc)) 4)
    ;; new node with direction not already in `node-children`
    (append-child 
     {::ge/direction
      (-> ge/directions
          (cset/difference (set (map ::ge/direction (children node-or-loc))))
          first)
      ::frequency 0
      ::children {}})))

(s/fdef tree-simulate
  :args (s/and (s/cat :tree-node (s/or :node ::tree-node
                                       :loc (s/tuple ::tree-node any?))
                      :game-state ::gs/game-state
                      :sim-size nat-int?))
  :ret ::tree-node)

(defn- root-node [direction]
  {::frequency 0 ::children {} ::ge/direction direction})

(defn- root-zipper [direction]
  (zip/zipper (comp map? ::children)
              (comp vals ::children)
              make-node
              {::frequency 0                 
               ::ge/direction direction
               ::children {}}))

(defmulti tree-simulate
  "Simulates `sim-size` steps of a game from `game-state`, with
  exploration data at current step stored in `tree-node`.
  Returns tree node with updated data."
  dispatch-zipper)

(defmethod tree-simulate :basic
  [tree-node game-state sim-size]
  (let [next-state (ge/move-player game-state (::ge/direction tree-node))]
    (-> (cond
          (< (::gs/score game-state) (::gs/score next-state))
          (assoc tree-node ::value 0)

          (or (zero? sim-size) (not (= (::gs/status next-state) :active)))
          (assoc tree-node ::value (worst-value game-state))

          :else
          (let [updated-node (update-children tree-node)
                next-node (min-child updated-node ::frequency)]
            (-> updated-node
                (assoc-in [::children (::ge/direction next-node)]
                          (tree-simulate next-state (dec sim-size) next-node))
                (#(assoc % ::value (inc (::value (min-child % ::value))))))))
        (update ::frequency inc))))

(defn- move-to-min-child
  "Moves `loc` down to the child that has the minimum value according to
  `sort-fn`"
  [loc sort-fn]
  (loop [current-loc (zip/down loc) min-loc current-loc]
    (if-let [next-loc (zip/right current-loc)]
      (recur next-loc
             (if (< (-> next-loc zip/node sort-fn)
                    (-> min-loc zip/node sort-fn))
               next-loc min-loc))
      min-loc)))

(defn- descend-simulation
  [loc game-state sim-size]
  (let [next-state (ge/move-player game-state (-> loc zip/node ::ge/direction))]
    (-> (cond
          (< (::gs/score game-state) (::gs/score next-state))
          (-> loc (zip/edit #(assoc % ::value 0)))

          (or (zero? sim-size) (not (= (::gs/status next-state) :active)))
          (-> loc (zip/edit #(assoc % ::value (worst-value game-state))))

          :else
          (-> loc
              update-children
              (move-to-min-child ::frequency)
              (recur next-state (dec sim-size)))))))

;; Implementation using a double recur rather than a non-tail
;; recursive call, for efficiency.
(defmethod tree-simulate :zipper
  [loc game-state sim-size]
  (loop [current-loc (descend-simulation loc game-state sim-size)]
    (let [updated-loc
          (cond-> current-loc
            true (zip/edit #(update % ::frequency inc))
            
            (not-empty (children current-loc))
            (zip/edit #(assoc % ::value (inc (::value (min-child % ::value))))))]
      (if-let [parent-loc (zip/up updated-loc)]
        (recur parent-loc)
        updated-loc))))


(defn- simulate-games
  [game-state node nb-sims]
  (let [simulate-once-from-node
        #(tree-simulate % game-state (max-sim-size game-state))]    
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

(defn- compute-root-node [this world]
  (let [simulate-on-each-direction
        #(simulate-games
          (::gs/game-state world)
          (root-zipper %)
          (/ (-> this :nb-sims) (count ge/directions)))]
    (->> ge/directions
         (pmap simulate-on-each-direction)
         (map node)
         (make-node {}))))

(s/def ::options (s/map-of #{:nb-sims} pos-int?))

(defrecord TreeExplorationPlayer [nb-sims]
  aip/Player
  (init-player [this opts world]
    (assert (s/valid? ::options opts))
    (assoc this :nb-sims (-> opts (:nb-sims default-nb-sims))))
  
  (update-player [this world]
    (-> this
        (assoc :root-node (compute-root-node this world))
        (#(assoc % :next-movement
                 (->  % :root-node (min-child ::value) ::ge/direction))))))
