(ns claby.ai.xp
  "Namespace to experiment stuff peacefully.
  Not required by any other namespace"
  (:require [claby.ai.main :as aim]
            [claby.ai.players.tree-exploration :as te]
            [claby.ai.world :as aiw]
            [claby.game.events :as ge]
            [claby.game.generation :as gg]))


(defn explore-update [direction f]
  (let [tree-sims (atom [])
        sim-games (var-get #'te/simulate-games)
        tree-sim te/tree-simulate]
    (with-redefs
      [te/simulate-games ;; ignore other directions
       (fn [gs nd ns]
         (if (= (::ge/direction nd) direction)
           (sim-games gs nd ns)
           (assoc nd ::te/value 4242)))
       te/tree-simulate
       (fn [tn gs ss]
         (when (= ss (#'te/max-sim-size gs))
           (swap! tree-sims #(conj % [])))
         (let [res (tree-sim tn gs ss)]
           (swap! tree-sims
                  (fn [v]
                    (update v (dec (count v))
                            #(conj % {(::ge/direction res) (::te/value res)}))))
           res))]
      (f))
    @tree-sims))

(fn []
  (let [initial-world
        (aiw/get-initial-world-state
         (last (gg/generate-game-states 6 25 43)))
        args-format-string
        "-v WARNING -t tree-exploration -n %s -o '{:node-constructor root-%s}'"
        get-args
        (fn [nb constr]
          (aim/parse-run-args args-format-string nb constr))
        node-impl-state (aim/run (get-args 4 "node") initial-world)]
    (println (aiw/data->string (:world node-impl-state)))
    (te/node-path (-> node-impl-state :player :root-node))))

