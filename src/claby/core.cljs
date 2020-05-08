(ns ^:figwheel-hooks claby.core
  "Entry point to run the Claby game.

  See game.cljs for more about the game."
  (:require
   [goog.dom :as gdom]
   [clojure.test.check]
   [clojure.test.check.properties]
   [cljs.spec.alpha :as s]
   [cljs.spec.gen.alpha :as gen]
   [claby.game :as g]
   [claby.game.generation :as gg]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :refer [render]]))

(defonce game-size 10)

(defonce levels
  [{:message "Lapinette enceinte doit manger un maximume de fraises"
    ::gg/density-map {:fruit 5
                      :cheese 0}}
   {:message "Attention au fromage non-pasteurisé !"
    ::gg/density-map {:fruit 5
                      :cheese 3}}])

(defonce game-state (atom {}))
(defonce level (atom 0))

;;; Player movement
;;;;;;

;;; Scroll if player moves too far up / down

(s/fdef board-scroll
  :args (s/cat :state ::g/game-state)
  :ret (s/or :double (s/double-in 0 1) :nil nil?))

(defn board-scroll
  "Returns the value of scroll needed so that player remains visible, as
  a fraction of the window height. If player is on top third of board,
  scroll to 0, on bottom-third, scroll to mid-page."
  [{:keys [::g/player-position ::g/game-board], :as state}]
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
(defonce jq (js* "$"))
(defonce gameMusic (js/Audio. "neverever.mp3"))
(defonce scoreSound (js/Audio. "coin.wav"))
(defonce sounds
  {:over (js/Audio. "over.wav")
   :won (js/Audio. "won.mp3")
   :nextlevel (js/Audio. "nextlevel.wav")})

(set! (.-loop gameMusic) true)

(defn start-game [elt-to-fade]
  (.addEventListener js/window "keydown" move-player)
  (swap! game-state #(g/init-game-state
                      (gg/create-nice-board game-size (levels @level))))
  (-> (.play gameMusic))
  (.fadeTo (jq "#h") 1000 1)
  (.fadeOut (jq elt-to-fade) 1000))

(defn animate-game
  ([status callback]
   (.scroll js/window 0 0)
   (.removeEventListener js/window "keydown" move-player) ;; freeze player
   (.pause gameMusic)
   (set! (.-onended (sounds status)) callback)
   (.play (sounds status))
   (.fadeTo (jq "#h") 2000 0)
   (.fadeIn (jq (str ".game-" (name status))) 2000))

  ([status] (animate-game status nil)))

(defn game-transition [status]
  (cond
    (and (= status :won) (< @level (dec (count levels))))
    (do (swap! level inc)
        (animate-game :nextlevel #(start-game ".game-nextlevel")))
    (some #{status} #{:won :over})
    (animate-game status))
  [:div])

(defn get-app-element []
  (gdom/getElement "app"))

(defn show-score
  [score]
  (if (pos? score) (.play scoreSound))
  [:div.score [:span (str "Score : " score)]])

(defn claby []
  [:div#lapyrinthe.row.justify-content-md-center
   [:h2.subtitle [:span (get-in levels [@level :message])]]
   [:div.col.col-lg-2]
   [:div.col-md-auto
    [show-score (@game-state ::g/score)]
    [:table (g/get-html-for-state @game-state)]]
   [:div.col.col-lg-2]
   [game-transition (@game-state ::g/status)]])


(defn mount [el]
  (.click (jq ".game-over button") #(start-game ".game-over"))
  (.click (jq "#surprise img")
          (fn []
            (.click (jq "#surprise img") nil)
            (start-game "#surprise")))
  (render [claby] el))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

(mount-app-element)


;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
