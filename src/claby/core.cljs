(ns ^:figwheel-hooks claby.core
  (:require
   [goog.dom :as gdom]
   [clojure.spec.alpha :as s]
   [claby.game :as g]
   [reagent.core :as reagent :refer [atom]]))

(println "Top of claby.core.")
  
(s/fdef get-html-from-state
  :args ::g/game-state
  :ret   (s/and vector?
                #(= (first %) :table)))

(defn- get-html-from-line
  "Generates html for a game board row given row index, row and player position"
  [index row position]
  (->> row
       (map-indexed ; for each cell of the row
        #(vector
          (keyword (str "td." (name %2) (if (= position [index %1]) ".player")))))
       (concat [:tr])
       vec))

(defn get-html-from-state
  "Given a game state, generates the (reagent) html to render it.

  E.g. for a game board [[:empty :empty] [:wall :fruit]] with player
  position [0 1] it should generate
  [:table [:tr [:td.empty] [:td.empty.player]] [:tr [:td.wall] [:td.fruit]]]"
  [state]
  (let [{board ::g/game-board position ::g/player-position} state]
    (->> board ; for each row of the board
         (map-indexed #(get-html-from-line %1 %2 position))
         (concat [:table])
         vec)))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Hello world!"}))

(defn get-app-element []
  (gdom/getElement "app"))

(defn hello-world []
  [:div
   [:h1 (:text @app-state)]
   [:h3 "Claby world."]])

(defn mount [el]
  (reagent/render-component [hello-world] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
