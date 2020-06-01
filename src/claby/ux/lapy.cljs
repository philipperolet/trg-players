(ns ^:figwheel-hooks claby.ux.lapy
  "Entry point to the game, with a creative ux for Lapyrinthe,
  aimed at making people play."
  (:require
   [clojure.test.check]
   [clojure.test.check.properties]
   [cljs.spec.alpha :as s]
   [cljs.spec.gen.alpha :as gen]
   [reagent.dom.server :refer [render-to-static-markup]]
   [claby.game.state :as gs]
   [claby.ux.base :as ux]))

(defonce jq (js* "$"))
(defonce gameMusic (js/Audio. "neverever.mp3"))
(defonce scoreSound (js/Audio. "coin.wav"))
(defonce sounds
  {:over (js/Audio. "over.wav")
   :won (js/Audio. "won.mp3")
   :nextlevel (js/Audio. "nextlevel.wav")})

(defonce music-on (atom true))

(set! (.-loop gameMusic) true)

(defn next-level-callback []
  (.animate (jq "#h h2.subtitle") (clj->js {:top "0em" :font-size "1.2em"}) 2500
            (fn []
              (.removeClass (jq "#h h2.subtitle") "initial"))))

(defonce start-level-data
  {:initial ["#surprise" #(.fadeOut (jq "#h h1") 2000)]
   :game-over [".game-over" nil]
   :nextlevel [".game-nextlevel" next-level-callback]})

(defn final-animation [i]
  (cond

    (< i 6)
    (do (.remove (jq ".game-won img"))
        (.append (jq ".game-won") (str "<img src=\"img/ending/" i ".gif\">"))
        (-> (jq ".game-won img")
            (.hide)
            (.fadeIn 500)
            (.delay 4300)
            (.fadeOut 500 #(final-animation (inc i)))))

    (= i 6)
    (do (.remove (jq ".game-won img"))
        (.append (jq ".game-won") "<img src=\"img/ouej.png\">")
        (-> (jq ".game-won img")
            (.css "height" "0.1em")
            (.css "width" "0.1em")
            (.animate
             (clj->js {:height "33%" :width "33%"})
             (clj->js
              {:duration 1000
               :step (fn [now fx]
                       (this-as this
                         (.css (jq this) "transform" (str "rotate(" (* now 360) "deg)"))))}))
            (.delay 2000)
            (.fadeOut 500 #(final-animation 7))))

    (= i 7)
    (do (.remove (jq ".game-won img"))
        (.append (jq ".game-won") (str "<img src=\"img/ending/6.gif\">"))
        (let [end-par [:p.end [:span "The end."] [:br] [:span.slide "__The end__"]]
              ps-par [:p.ps [:span "P.S. : C'est un garçon."][:br][:span.slide "__P.S. : C'est un garçon.__"]]]
          (.append (jq ".game-won") (render-to-static-markup end-par))
          (.append (jq ".game-won") (render-to-static-markup ps-par))
          (-> (jq ".game-won img")
              (.hide)
              (.fadeIn 500 #(final-animation 8)))))

    (= i 8)
    (.animate (jq ".game-won p.end span.slide") (clj->js {:left "5em"}) 2000
              (fn []
                (.setTimeout js/window
                             #(.animate (jq ".game-won p.ps span.slide") (clj->js {:left "10em"}) 2000) 1000)))))

(defn between-levels []
  (.css (jq "#h h2.subtitle") (clj->js {:top "" :font-size "" :opacity 1}))
  (.addClass (jq "#h h2.subtitle") "initial")
  (.css (jq "#h h2.subtitle span") "color" (get-in ux/levels [@ux/level :message-color])))

(defn- volume-toggle []
  (if @music-on
    (do (.pause gameMusic)
        (.remove (jq "#lapy-arrows button img"))
        (.append (jq "#lapy-arrows button") "<img src=\"img/mute.png\">"))
    (do (.play gameMusic)
        (.remove (jq "#lapy-arrows button img"))
        (.append (jq "#lapy-arrows button") "<img src=\"img/volume.png\">")))
  (swap! music-on not))

(defonce lapy-ux
  (reify ux/ClapyUX
    
    (init [this]
      (.click (jq ".game-over button")
              (fn []
                (ux/start-game this)))
      (.click (jq "#lapy-arrows button") volume-toggle)
      (.click (jq "#surprise img")
              (fn []
                (.requestFullscreen (.-documentElement js/document))
                (.click (jq "#surprise img") nil)
                (ux/start-game this))))

    (start-level [this]
      ;; Choose element to fade and callback depending on
      ;; whether the surprise ? has already been clicked and hidden or not
      (let [[elt-to-fade callback]
            (cond
              (.is (jq "#surprise") ":visible")
              (start-level-data :initial)

              (.is (jq ".game-over") ":visible")
              (start-level-data :game-over)
              
              :else
              (start-level-data :nextlevel))]

        ;; music and fading to begin level, and game activity
        (if @music-on (-> (.play gameMusic)))
        (.fadeTo (jq "#h") 1000 1
                 (fn []
                   (swap! ux/game-state #(assoc % ::gs/status :active))
                   (when callback (callback))))
        (.fadeOut (jq elt-to-fade) 1000)))
    
    (animate-transition [this transition-type]
      (.scroll js/window 0 0)
      (.pause gameMusic)
      
      (let [on-sound-end-callback
            (if (= transition-type :nextlevel) #(ux/start-game this))

            in-between-callback
            (case transition-type
              :nextlevel between-levels
              :won #(final-animation 0)
              nil)]
        
        (set! (.-onended (sounds transition-type)) on-sound-end-callback)
        (.play (sounds transition-type))
        (.fadeTo (jq "#h h2.subtitle") 100 0)
        (.setTimeout js/window
                     (fn []
                       (.fadeTo (jq "#h") 500 0)
                       (.fadeIn (jq (str ".game-" (name transition-type))) 1000 in-between-callback))
                     1000)))
    
    (score-update [this score]
      (when (pos? score)
        (.load scoreSound)
        (.play scoreSound)))
    
    (enemy-style [this type]
      (str "{background-image: url(../img/" type ".gif)}"))))

(ux/run-game lapy-ux)
