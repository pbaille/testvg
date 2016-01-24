(ns testvg.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [cljs-http.client :as http]
            [cljs.core.async :as async :refer [<!]]
            [cljs-time.core :as time]
            [cljs-time.format :as tformat]
            [garden.color :as gc]))

(enable-console-print!)

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to testvg"]
   [:div [:a {:href "/about"} "go to about page"]]])

(defn about-page []
  [:div [:h2 "About testvg"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

(defn test1-comp []
  (let [seconds-elapsed (atom 0)]
    (fn []
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div
       "Seconds Elapsed: " @seconds-elapsed])))

(defn test1 []
  [test1-comp])

(defn test2-comp []
  (let [seconds-elapsed (atom 0)]
    (fn []
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div
       [:a {:href "/"} "home"]
       [:div "Seconds Elapsed: " @seconds-elapsed]
       [:div {:style {:font-size :60px
                      :color     :lightskyblue
                      :padding   :30px}} @seconds-elapsed]])))

(defn test2 []
  [test2-comp])

(defn test3-comp []
  (let [seconds-elapsed (atom 0)
        off? (atom false)]
    (fn []
      (when-not @off? (js/setTimeout #(swap! seconds-elapsed inc) 1000))
      [:div
       [:a {:href "/"} "home"]
       [:div {:on-click (fn [_] (swap! off? not))
              :style    {:color (if @off? :green :red)}}
        (if @off? "start" "stop")]
       [:div "Seconds Elapsed: " @seconds-elapsed]])))

(defn test3 []
  [test3-comp])

;; Volumes ----------------------------------------------------

;; helpers ---------

(defn swap-in! [ref path f & args]
  (apply swap! ref update-in path f args))

(defn reset-in! [ref path v]
  (reset! ref (assoc-in @ref path v)))

(defn time->str [x]
  (tformat/unparse
    (tformat/formatters :date-time)
    x))

(defn ago [delta-t]
  (time->str (time/minus (time/now) delta-t)))

(defn now []
  (time->str (time/now)))

(def str->time-unit-fn
  {"minute" time/minutes
   "second" time/seconds
   "hour"   time/hours})

(defn delta-t
  ([{:keys [unit val]}]
   (delta-t val unit))
  ([v unit]
   ((str->time-unit-fn unit) v)))

(defn interp [a b r]
  (int (+ a (* r (- b a)))))

(defn blend [c1 c2 ratio]
  (let [c1 (gc/restrict-rgb (gc/as-rgb c1))
        c2 (gc/restrict-rgb (gc/as-rgb c2))]
    (merge-with #(interp %1 %2 ratio) c1 c2)))

(defn rgb->str [{:keys [red green blue]}]
  (str "rgb(" red "," green "," blue ")"))

;; requests -------

(defn get-volume [delta-t cb]
  (async/take! (http/get "http://api.vigiglobe.com/api/statistics/v1/volume"
                         {:with-credentials? false
                          :query-params      {"project_id" "vgteam-TV_Shows"
                                              "timeFrom"   (ago delta-t)
                                              "timeTo"     (now)}})
               cb))

(comment
  (get-volume (time/minutes 2)
              (fn [r] (println (-> r :body :data :messages)))))

(defn get-volumes! [state]
  (let [{:keys [plot span on]} @state]
    (async/take! (http/get "http://api.vigiglobe.com/api/statistics/v1/volume"
                           {:with-credentials? false
                            :query-params      {"granularity" (:unit plot)
                                                "project_id"  "vgteam-TV_Shows"
                                                "timeFrom"    (ago (delta-t span))
                                                "timeTo"      (now)}})
                 (fn [r]
                   (let [val (mapv (partial apply +)
                                        (partition (:val plot)
                                                   (map second
                                                        (-> r :body :data :messages))))]
                     (reset-in! state [:xs] (if on (conj (vec (next val)) 0) val)))))))

(defn get-volume-from [from cb]
  (async/take! (http/get "http://api.vigiglobe.com/api/statistics/v1/volume"
                         {:with-credentials? false
                          :query-params      {"project_id" "vgteam-TV_Shows"
                                              "timeFrom"   (time->str from)
                                              "timeTo"     (now)}})
               cb))

(defn volumes-refresh! [state last-step]
  (println "refresh")
  (get-volume-from last-step
                   (fn [r]
                     (swap-in! state
                               [:xs]
                            #(conj (vec (butlast %)) (-> r :body :data :messages first second))))))

(defn get-messages [ref from to]
  (async/take! (http/get "http://api.vigiglobe.com/api/content/v1/messages"
                         {:with-credentials? false
                          :query-params      {"project_id" "vgteam-TV_Shows"
                                              "timeFrom"   (time->str from)
                                              "timeTo"     (time->str to)}})
               (fn [r]
                 (reset! ref (-> r :body :data)))))

(async/take! (http/get "http://api.vigiglobe.com/api/content/v1/messages"
                       {:with-credentials? false
                        :query-params      {"project_id" "vgteam-TV_Shows"
                                            "timeFrom"   (ago (delta-t 10 "second"))
                                            "timeTo"     (now)}})
             (fn [r]
               (println (map :text (-> r :body :data)))))

;; no internet! fake data ---------

(comment
  (defn fake-data []
    (vec (repeatedly 20 #(rand-nth (range 10)))))

  (defn get-volumes! [s]
    (reset-in! s [:xs] (fake-data)))

  (defn volumes-step! [s]
    (swap-in! s [:xs] #(conj (vec (next %)) (first %)))))

;; ---------------------------------

(defn delta-t-comp [ref label {:keys [val unit]} key]
  (let [cb (fn [field e]
             (reset-in! ref
                        [key field]
                        ((if (= field :val) int identity)
                          (.. e -target -value)))
             (get-volumes! ref))]
    [:span
     [:span (str label ": ")]
     [:input {:type      "number"
              :value     val
              :on-change (partial cb :val)
              :style     {:width      :50px
                          :text-align :right
                          :border     :none
                          :font-size  :12px}}]
     [:select
      {:value     unit
       :on-change (partial cb :unit)}
      (for [x ["second" "minute" "hour"]]
        ^{:key (gensym)}
        [:option {:value x}
         (if (= 1 val) x (str x "s"))])]]))

(defn color-picker [current cb]
  [:select
   {:value     current
    :on-change (fn [e] (cb (.. e -target -value)))}
   (for [[k v] gc/color-name->hex]
     ^{:key (gensym)}
     [:option {:value v} (name k)])])

(defn message-comp [state]
  (let []
    [:div state]))

(defn messages-list [from to]
  (let [xs (atom ())
        _ (get-messages xs from to)]
    (fn []
      [:div.messages-list
       (for [x xs]
         ^{:key (gensym)}
         [message-comp x])])))

(defn volumes-comp [state]
  (let [state (atom state)
        last-step (cljs.core/atom nil)
        inter (cljs.core/atom nil)]
    (get-volumes! state)
    (fn []
      (let [{:keys [width height plot span on colors rate xs focus]} @state
            [c1 c2] colors]
        (js/clearTimeout @inter)
        (when on
          (let [step? (or (not @last-step)
                          (time/before? (time/plus @last-step (delta-t plot))
                                        (time/now)))]

            (if step?
              (do (reset! last-step (time/now))
                  (swap-in! state [:xs] #(vec (next (conj % 0))))))
            (reset! inter
                    (js/setInterval (fn [] (volumes-refresh! state @last-step))
                                    (time/in-millis (delta-t rate))))))

        [:div
         [:div.options
          [:span {:on-click (fn [_] (swap-in! state [:on] not))
                  :style    {:color (if on :red :green)}}
           (if on "stop" "start")]
          (delta-t-comp state "plot" plot :plot)
          (delta-t-comp state "span" span :span)
          (color-picker c1 (fn [v] (reset-in! state [:colors 0] v)))
          (color-picker c2 (fn [v] (reset-in! state [:colors 1] v)))]
         [:div.chart
          {:style {:height  :400px
                   :width   :800px
                   :padding :20px}}
          (let [max (apply max xs)
                plot-width (/ width (count xs))
                ls @last-step]
            (for [x xs
                  :let [rat (/ x max)]]
              ^{:key (gensym)}
              [:div {:on-click (fn [_])
                     :style    {:box-sizing       :border-box
                                :border           "2px solid white"
                                :display          :inline-block
                                :height           (str (int (+ 10 (* height rat))) "px")
                                :width            (str plot-width "px")
                                :background-color (rgb->str (blend c1 c2 rat))}}]))]
         (if focus
           [messages-list focus (time/plus focus (delta-t plot))])
         [:div "<h1>YOP</h1>"]]))))

(defn bonus []
  [volumes-comp
   {:width 600
    :height 400
    :rate   {:unit "second"
             :val  1}
    :plot   {:unit "second"
             :val  10}
    :span   {:unit "minute"
             :val  6}
    :on     true
    :colors [(gc/color-name->hex :lightskyblue)
             (gc/color-name->hex :lightcoral)]
    :focus nil}])



;; -------------------------
;; Routes

(secretary/defroute "/" []
                    (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
                    (session/put! :current-page #'about-page))

(secretary/defroute "/#/test1" []
                    (session/put! :current-page #'test1))

(secretary/defroute "/#/test2" []
                    (session/put! :current-page #'test2))

(secretary/defroute "/#/test3" []
                    (session/put! :current-page #'test3))

(secretary/defroute "/#/bonus" []
                    (session/put! :current-page #'bonus))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (mount-root))

