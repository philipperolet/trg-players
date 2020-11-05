(ns claby.ai.xp
  (:require [claby.ai.main :as aim]
            [claby.ai.players.tree-exploration :as te]
            [claby.ai.world :as aiw]
            [claby.game.events :as ge]
            [claby.game.generation :as gg]
            [clojure.zip :as zip]))

(defn explore-update-z [direction f]
  (let [tree-sims (atom [])
        sim-games (var-get #'te/simulate-games)
        tree-sim (var-get #'te/tree-simulate)
        zip-up zip/up]
    (with-redefs
      [te/simulate-games ;; ignore other directions
       (fn [gs nd ns]
         (if (= (::ge/direction (zip/node nd)) direction)
           (sim-games gs nd ns)
           (zip/edit nd #(assoc % ::te/value 4242))))
       te/tree-simulate
       (fn [tn gs ss]
         (swap! tree-sims #(conj % []))
         (with-redefs
           [zip/up
            (fn [loc]
              (swap! tree-sims
                     (fn [v]
                       (update v (dec (count v))
                               #(conj % {(::ge/direction (zip/node loc)) (::te/value (zip/node loc))}))))
              (zip-up loc))]
           (tree-sim tn gs ss)))]
      (f))
    @tree-sims))

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
        node-impl-state (aim/run (get-args 4 "node") initial-world)
        zipper-impl-state (aim/run (get-args 4 "zipper") initial-world)]
    (println (aiw/data->string (:world node-impl-state)))
    (te/node-path (-> node-impl-state :player :root-node))
    (explore-update-z :left
                    (fn []
                      (let [res
                            (aim/run (get-args 1 "zipper")
                              (:world zipper-impl-state)
                              (:player zipper-impl-state))]
                        (println (aiw/data->string (:world res)))
                        (te/node-path (-> res :player :root-node)))))))

