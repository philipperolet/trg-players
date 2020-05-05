(ns ^:figwheel-hooks claby.core
  "Entry point to run the Claby game.

  See game.cljs for more about the game."
  (:require
   [goog.dom :as gdom]
   [cljs.spec.alpha :as s]
   [claby.game :as g]
   [claby.game.generation :refer [create-nice-board]]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :refer [render]]))

(def jquery (js* "$"))

(defonce game-size 27)

(defonce game-state (atom (g/init-game-state (create-nice-board game-size))))

;;;
;;; Conversion of game state to Hiccup HTML
;;; 

(s/fdef get-html-for-state
  :args (s/cat :state ::g/game-state)
  :ret  (s/and vector?
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

;;;
;;; Player movement
;;;

;;; Scroll if player moves too far up / down

(s/fdef board-scroll
  :args (s/cat :state ::g/game-state)
  :ret (s/double-in 0 1))

(defn board-scroll
  "Returns the value of scroll needed so that player remains visible, as
  a fraction of the window height. If player is on top third of board,
  scroll to 0, on bottom-third, scroll to mid-page."
  [{:keys [::g/player-position ::g/game-board], :as game-state}]
  (let [size (count game-board)]
    (cond
      (< (player-position 0) (* size 0.4)) 0
      (> (player-position 0) (* size 0.7)) 0.5)))

(defn move-player
  "Moves player on the board by changing player-position"
  [e]
  (let [direction (case (.-key e)
                    ("ArrowUp" "e" "E") :up
                    ("ArrowDown" "d" "D") :down
                    ("ArrowLeft" "s" "S") :left
                    ("ArrowRight" "f" "F") :right
                    :no-movement)]
    (when (not= direction :no-movement)
      (.preventDefault e)
      (swap! game-state g/move-player direction)
      (when-let [scroll-value (board-scroll @game-state)]
        (.scroll js/window 0 (* scroll-value (.-innerHeight js/window)))))))

  
;;;
;;; Component & app rendering
;;;

(defn get-app-element []
  (gdom/getElement "app"))

(defn show-score
  [score]
  (if (pos? score) (.play (js/Audio. "coin.wav")))
  [:div.score [:span (str "Score : " score)]])

(defn claby []
  [:div#lapyrinthe.row.justify-content-md-center
   [:div.col.col-lg-2]
   [:div.col-md-auto
    [show-score (@game-state ::g/score)]
    (get-html-for-state @game-state)]
   [:div.col.col-lg-2]])

(defn mount [el]
  (render [claby] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(.addEventListener js/window "keydown" move-player)
(def gameMusic (js/Audio. "neverever.mp3"))
(set! (.-loop gameMusic) true)
(.click (jquery "#surprise img")
        (fn []
          (-> (.play gameMusic))
          (.fadeOut (jquery "#surprise") 3000)
          (.click (jquery "#surprise img") nil)))

(mount-app-element)


;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
