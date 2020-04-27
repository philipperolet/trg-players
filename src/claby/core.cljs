(ns ^:figwheel-hooks claby.core
  (:require
   [goog.dom :as gdom]
   [clojure.spec.alpha :as s]
   [claby.game :as g]
   [reagent.core :as reagent :refer [atom]]))

;;;
;;; Conversion of game state to HTML
;;; 

(s/fdef get-html-for-state
  :args ::g/game-state
  :ret   (s/and vector?
                #(= (first %) :table)))

(defn- get-html-for-cell
  "Generates html for a game board cell"
  [cell-index cell player-position]
  (-> (str "td." (name cell) (if (= player-position cell-index) ".player"))
      keyword
      vector
      (conj {:key (str "claby-" (cell-index 0) "-" (cell-index 1))})))

(defn- get-html-for-line
  "Generates html for a game board row"
  [row-index row player-position]
  (->> row ; for each cell of the row
       (map-indexed #(get-html-for-cell [row-index %1] %2 player-position))
       (concat [:tr {:key (str "claby-" row-index)}])
       vec))

(defn get-html-for-state
  "Given a game state, generates the (reagent) html to render it.

  E.g. for a game board [[:empty :empty] [:wall :fruit]] with player
  position [0 1] it should generate
  [:table [:tr [:td.empty] [:td.empty.player]] [:tr [:td.wall] [:td.fruit]]]"
  [state]
  (let [{board ::g/game-board position ::g/player-position} state]
    (->> board
         (map-indexed #(get-html-for-line %1 %2 position))
         (concat [:tbody])
         vec
         (vector :table))))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Claby ?!"}))
(defonce game-state (atom (g/create-game)))

(defn move-player
  "Moves player on the board by changing player-position"
  [e]
  (swap! game-state assoc ::g/player-position (vec (map inc (@game-state ::g/player-position)))))

(defn get-app-element []
  (gdom/getElement "app"))

(defn hello-world []
  [:div
   [:h1 (:text @app-state)]
   [:h2 (str (@game-state ::g/player-position))]
   [:div.board (get-html-for-state @game-state)]])

(defn mount [el]
  (.addEventListener js/window "keydown" move-player)
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
