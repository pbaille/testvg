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
            [garden.color :as gc]
            cljsjs.c3))

(enable-console-print!)

;; helpers ---------

(defn swap-in! [ref path f & args]
  (apply swap! ref update-in path f args))

(defn reset-in! [ref path v]
  (reset! ref (assoc-in @ref path v)))

(defn time->str [x]
  (tformat/unparse
    (tformat/formatters :date-time)
    x))

(defn str->time [x]
  (tformat/parse
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

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to testvg"]
   [:div [:a {:href "/about"} "go to about page"]]])

(defn about-page []
  [:div [:h2 "About testvg"]
   [:div
    [:p "Hi!"]
    [:p "Here's my try to your tests!"]
    [:p "the first four were easy."]
    [:p "I've struggled a bit for the line chart, so I decided to use c3, I haven't found a good way to integrate it properly with reagent component, so my implementation is kind of dirty I think... I've tried to use c3's timeseries type graph but i've encountered some issue with time formatting and haven't being able to use it"]
    [:p "As extra i've done a little interactive plot chart also based on messages volumes that can also fetch and display messages."]
    [:p "I wish I had time to factorize the code better:"
     [:br]
     "- API request should be embedded in a simple function."
     [:br]
     "- styles are messy."
     [:br]
     "- the extra component should be splitted into several sub components."]
    [:p "It was fun to do, I wish I had more time to work on it."]
    [:p "hope to hear about you soon!"]
    [:p "Pierre"]
    ]])

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
       [:div "Seconds Elapsed: " @seconds-elapsed]
       [:div {:style {:font-size :60px
                      :color     :lightskyblue
                      :padding   :30px}} @seconds-elapsed]])))

(defn test2 []
  [test2-comp])

(defn test3-comp []
  (let [seconds-elapsed (atom 0)
        off? (atom false)
        timeout (cljs.core/atom nil)]
    (fn []
      (if @off?
        (js/clearTimeout @timeout)
        (reset! timeout (js/setTimeout #(swap! seconds-elapsed inc) 1000)))
      [:div
       [:div {:on-click (fn [_] (swap! off? not))
              :style    {:color (if @off? :green :red)}}
        (if @off? "start" "stop")]
       [:div "Seconds Elapsed: " @seconds-elapsed]])))

(defn test3 []
  [test3-comp])

(defn counter []
  (let [seconds-elapsed (atom 0)
        off? (atom false)
        timeout (cljs.core/atom nil)]
    (fn []
      (if @off?
        (js/clearTimeout @timeout)
        (reset! timeout (js/setTimeout #(swap! seconds-elapsed inc) 1000)))
      [:div.counter
       [:i {:on-click (fn [_] (swap! off? not))
            :style    {:color (if @off? :green :red)}
            :class    (if @off? "icon-play" "icon-pause")}]
       [:span.digit @seconds-elapsed]])))

(defn bonus-comp []
  (let [timers (atom {})]
    (fn []
      [:div.bonus
       [:div.new {:on-click (fn [_] (swap! timers assoc (gensym) (counter)))} "new counter"]
       (for [[id t] (sort @timers)]
         ^{:key id}
         [:div
          [t]
          [:span {:on-click (fn [_] (swap! timers dissoc id))}
           [:i.icon-cancel]]])])))

(defn bonus []
  [bonus-comp])

;; ----------------------------------------------------------------------
;; Extra

;; requests -------

(defn get-volumes! [state]
  (let [{:keys [plot span on]} @state
        now (now)]
    (async/take! (http/get "http://api.vigiglobe.com/api/statistics/v1/volume"
                           {:with-credentials? false
                            :query-params      {"granularity" (:unit plot)
                                                "project_id"  "vgteam-TV_Shows"
                                                "timeFrom"    (ago (delta-t span))
                                                "timeTo"      now}})
                 (fn [r]
                   (let [val (mapv #(reduce (fn [[t x] [_ y]] [t (+ x y)]) (first %) (next %))
                                   (partition (:val plot)
                                              (-> r :body :data :messages)))]
                     (reset-in! state [:xs] (if on (conj (vec (next val)) [now 0]) val)))))))

(defn get-volume-from [from cb]
  (async/take! (http/get "http://api.vigiglobe.com/api/statistics/v1/volume"
                         {:with-credentials? false
                          :query-params      {"project_id" "vgteam-TV_Shows"
                                              "timeFrom"   (time->str from)
                                              "timeTo"     (now)}})
               cb))

(defn volumes-refresh! [state last-step]
  (get-volume-from last-step
                   (fn [r]
                     (swap-in! state
                               [:xs]
                               #(conj (vec (butlast %)) (-> r :body :data :messages first))))))

(defn get-messages [state from to]
  (reset-in! state [:fetching-messages] true)
  (async/take! (http/get "http://api.vigiglobe.com/api/content/v1/messages"
                         {:with-credentials? false
                          :query-params      {"project_id" "vgteam-TV_Shows"
                                              "limit"      200
                                              "timeFrom"   (time->str from)
                                              "timeTo"     (time->str to)}})
               (fn [r]
                 ; this would be better than 2 state mutation in a row
                 ; (swap! state assoc :fetching-messages false :messages (-> r :body :data))
                 ; but it doesn't do the job :(
                 (reset-in! state [:fetching-messages] false)
                 (reset-in! state [:messages] (-> r :body :data)))))

;; comps ---------------------------------

(defn delta-t-comp [ref label {:keys [val unit]} key]
  (let [cb (fn [field e]
             (reset-in! ref
                        [key field]
                        ((if (= field :val) int identity)
                          (.. e -target -value)))
             (get-volumes! ref))]
    [:div.delta-t-comp
     [:div.label label]
     [:input {:type      "number"
              :value     val
              :on-change (partial cb :val)}]
     [:select
      {:value     unit
       :on-change (partial cb :unit)}
      (for [x ["second" "minute"]]
        ^{:key (gensym)}
        [:option {:value x}
         (if (= 1 val) x (str x "s"))])]]))

(defn color-picker [label current cb]
  [:div.color-picker
   [:div.label label]
   [:select.color
    {:value     current
     :on-change (fn [e] (cb (.. e -target -value)))}
    (for [[k v] gc/color-name->hex]
      ^{:key (gensym)}
      [:option {:value v} (name k)])]])

(defn messages-list [messages]
  [:div.messages-list
   (for [x messages]
     ^{:key (gensym)}
     [:div.message
      [:div.author (:author x)]
      [:div.text (:text x)]])])

(defn volumes-comp [state]
  (let [state (atom state)
        last-step (cljs.core/atom nil)
        inter (cljs.core/atom nil)]
    (get-volumes! state)
    (fn []
      (let [{:keys [width height plot
                    span on colors rate
                    xs messages
                    show-options
                    fetching-messages]} @state
            [c1 c2] colors]
        (js/clearTimeout @inter)
        (when on
          (let [step? (or (not @last-step)
                          (time/before? (time/plus @last-step (delta-t plot))
                                        (time/now)))]

            (if step?
              (do (reset! last-step (time/now))
                  (swap-in! state [:xs] #(vec (next (conj % [(now) 0]))))))
            (reset! inter
                    (js/setInterval (fn [] (volumes-refresh! state @last-step))
                                    (time/in-millis (delta-t rate))))))

        [:div

         [:div.topbar
          [:i
           {:on-click (fn [_] (swap-in! state [:on] not))
            :style    {:float :left
                       :color (if on c2 c1)}
            :class    (if on "icon-pause" "icon-play")}]
          [:i.icon-cog
           {:on-click (fn [_] (swap-in! state [:show-options] not))}]]
         (when show-options
           [:div.options
            [:i.icon-cancel {:on-click (fn [_] (reset-in! state [:show-options] false))}]
            (delta-t-comp state "division" plot :plot)
            (delta-t-comp state "total time" span :span)
            (delta-t-comp state "refresh rate" rate :rate)
            (color-picker "color 1" c1 (fn [v] (reset-in! state [:colors 0] v)))
            (color-picker "color 2" c2 (fn [v] (reset-in! state [:colors 1] v)))])

         [:div.chart
          {:style {:height (str height "px")
                   :width  (str width "px")}}
          (let [max (apply max (map second xs))
                plot-width (/ width (count xs))
                ls @last-step]
            (for [[t x] xs
                  :let [rat (/ x max)
                        t (str->time t)]]
              ^{:key (gensym)}
              [:div.plot
               {:on-click (fn [_] (get-messages state t (time/plus t (delta-t plot))))
                :style    {:height           (str (int (+ 10 (* (- height 10) rat))) "px")
                           :width            (str plot-width "px")
                           :background-color (rgb->str (blend c1 c2 rat))}}]))]

         (cond
           fetching-messages [:div "fetching messages..."]
           (seq messages) [messages-list messages]
           :else [:div "click a plot to see its content"])]))))

(defn extra []
  [volumes-comp
   {:width        600
    :height       400
    :rate         {:unit "second"
                   :val  2}
    :plot         {:unit "second"
                   :val  20}
    :span         {:unit "minute"
                   :val  15}
    :on           true
    :colors       [(gc/color-name->hex :lightskyblue)
                   (gc/color-name->hex :lightcoral)]
    :show-options false}])

;; line charts -----------------------

(def prepend (comp vec cons))

(defn format-columns [data label]
  [(prepend label (map second data))])

(defn spline-graph [{:keys [id label data granularity span]}]
  (atom
    {:bindto      id
     :data        {:type    "spline"
                   :columns (format-columns data label)}
     :label       label

     :granularity granularity
     :drawn       false
     :span        span}))

(defn draw! [ref]
  (swap! ref
         assoc
         :var (.generate js/c3 (clj->js @ref))
         :drawn true))

(defn load! [ref]
  (.load (:var @ref) (clj->js {:columns (-> @ref :data :columns)})))

(defn fetch-spline-graph-data! [ref]
  (let [{:keys [label drawn granularity span]} @ref]
    (async/take! (http/get "http://api.vigiglobe.com/api/statistics/v1/volume"
                           {:with-credentials? false
                            :query-params      {"granularity" granularity
                                                "project_id"  "vgteam-TV_Shows"
                                                "timeFrom"    (time->str (time/minus (time/now) span))
                                                "timeTo"      (now)}})
                 (fn [r]
                   (reset-in! ref
                              [:data :columns]
                              (format-columns (-> r :body :data :messages)
                                              label))
                   (if drawn
                     (load! ref)
                     (draw! ref))))))

(defn spline-charts []
  (let [i1 (cljs.core/atom nil)
        i2 (cljs.core/atom nil)
        last-hour-volumes
        (spline-graph
          {:id          "#hour-chart"
           :label       "volume"
           :granularity "minute"
           :span        (time/hours 1)})

        last-minute-volumes
        (spline-graph
          {:id          "#minute-chart"
           :label       "volume"
           :granularity "second"
           :span        (time/minutes 1)})]
    (reagent/create-class
      {:component-did-mount
       (fn []
         (println "didmount")
         (fetch-spline-graph-data! last-hour-volumes)
         (fetch-spline-graph-data! last-minute-volumes)
         (reset! i1 (js/setInterval (fn [] (fetch-spline-graph-data! last-hour-volumes)) 60000))
         (reset! i2 (js/setInterval (fn [] (fetch-spline-graph-data! last-minute-volumes)) 5000)))
       :component-will-unmount
       (fn []
         (js/clearInterval @i2)
         (js/clearInterval @i1))
       :reagent-render
       (fn []
         [:div.spline-charts
          [:h2 "Messages Volume"]
          [:div.lab "last hour"]
          [:div#hour-chart.graph]
          [:div.lab "last minute"]
          [:div#minute-chart.graph]])})))

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

(secretary/defroute "/#/extra" []
                    (session/put! :current-page #'extra))

(secretary/defroute "/#/spline" []
                    (session/put! :current-page #'spline-charts))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (mount-root))

