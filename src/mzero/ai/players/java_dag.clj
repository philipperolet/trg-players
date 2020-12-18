(ns mzero.ai.players.java-dag
  "DagNodeImpl: nodes are shallow, no data except for position information on the
board (incl. dag map & board size). Data is held by the `dag-map`"
  (:require [mzero.game.events :as ge]
            [mzero.game.board :as gb]
            [mzero.ai.players.tree-exploration :as te]))

(defn- get-node-to-aggregate [direction1 direction2 children]
  (let [opposed-directions {:up :down, :down :up, :right :left, :left :right}]
    (-> children
        direction1
        (te/get-descendant [(opposed-directions direction1) direction2]))))

(defn- compute-merged-node-in-direction [direction children]
  (let [compute-frequency
        (fn [child-dir]
          (reduce
           #(+ %1 (or (te/frequency (get-node-to-aggregate %2 child-dir children)) 0))
           0 ge/directions))
        compute-value
        (fn [child-dir]
          (reduce #(min %1 (or (te/value (get-node-to-aggregate %2 child-dir children)) ##Inf))
                  ##Inf
                  ge/directions))]
    (te/map->TreeExplorationNodeImpl
     {::te/frequency (compute-frequency direction)
      ::te/value (compute-value direction)})))

(defrecord JavaDagImpl [values frequencies position board-size]
  te/TreeExplorationNode

  (append-child [this direction]
    (assert false "Should not be called.")
    #_(let [child-position
          (ge/move-position (-> this :position) direction (-> this :board-size))
          init-child-if-nil
          (fn [nd]
            (if nd nd (te/map->TreeExplorationNodeImpl
                       {::te/frequency 0
                        ::te/value ##Inf})))]
      (update this :dag-map #(update-in % child-position init-child-if-nil))))
  
  (-value [{:keys [values], [x y] :position}]
    (aget values x y))
  (-assoc-value [{:as this, :keys [values], [x y] :position} val_]
    (aset values x y val_)
    this)
  (-frequency [{:keys [frequencies], [x y] :position}]
    (aget frequencies x y))
  (-assoc-frequency [{:as  this, :keys [frequencies], [x y] :position} freq]
    (aset frequencies x y freq)
    this)
  (min-direction [{:as this, :keys [position board-size]} sort-key]
    (let [get-value-from-direction
          #(sort-key (te/get-child this %))]
      (apply min-key get-value-from-direction ge/directions)))
  
  (-children [{:as this, :keys [position board-size]}]
    (let [add-child-from-direction
          #(let [child (te/get-child this %2)]
             (assoc %1 %2
                    (te/map->TreeExplorationNodeImpl
                     {::te/value (te/value child)
                      ::te/frequency (te/frequency child)})))]
      (add-child-from-direction
       (add-child-from-direction
        (add-child-from-direction
         (add-child-from-direction {} :up)
         :right)
        :down)
       :left)))

  (get-child [this direction]
    (update this :position
            #(ge/move-position % direction (-> this :board-size))))
  
  (update-children-in-direction [this direction f]
    (let [next-node
          (->JavaDagImpl
           (-> this :values)
           (-> this :frequencies)
           (ge/move-position (-> this :position) direction (-> this :board-size))
           (-> this :board-size))]
      (f next-node)
      this))

  (-create-root-node [this children]
    (assoc (te/->TreeExplorationNodeImpl)
           ::te/value 0
           ::te/frequency Integer/MAX_VALUE
           ::te/children
           (reduce #(assoc %1 %2 (compute-merged-node-in-direction %2 children))
                   {}
                   ge/directions))))


(defn java-dag-node
  "DagNodeImpl constructor"
  [game-state]
  (let [board-size (count (::gb/game-board game-state))]
    (->JavaDagImpl
     (let [arr (make-array Double/TYPE board-size board-size)]
       (map #(java.util.Arrays/fill (aget arr %) ##Inf) (range board-size))
       arr)
     (make-array Double/TYPE board-size board-size)
     [0 0]
     board-size)))
