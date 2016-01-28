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

(defn time-str->hour-str [x]
  (subs x 11 19))

(defn ago [delta-t]
  (time->str (time/minus (time/now) delta-t)))

(defn now []
  (time->str (time/now)))

(def str->time-unit-fn
  {"minute" time/minutes
   "second" time/seconds
   "hour"   time/hours
   "day"   time/days})

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

;; timed tasks (timeout interval) --------

(defn ttask [cb ms once?]
  (cljs.core/atom
    {:callback cb
     :decay    ms
     :once?    once?
     :var      nil}))

(defn tt-do! [{:keys [callback decay once?] :as t}]
  (let [{:keys [callback decay once?]} @t]
    (swap! t assoc :var
           ((if once? js/setTimeout js/setInterval) callback decay))))

(defn tt-clear! [{:keys [var once?] :as t}]
  (let [{:keys [var once?]} @t]
    ((if once? js/clearTimeout js/clearInterval) var)
    (swap! t assoc :var nil)))

(defn interval [cb ms]
  (ttask cb ms false))

(defn timeout [cb ms]
  (ttask cb ms true))

;; requests ------------------------------

(def base-url "http://api.vigiglobe.com/api/")

(defn default-params []
  {"project_id" "vgteam-TV_Shows"
   "timeTo"     (now)})

(defn request [{:keys [section params callback]}]
  (async/take! (http/get (str base-url section)
                         {:with-credentials? false
                          :query-params      (merge (default-params) params)})
               (or callback identity)))

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to testvg"]
   [:div [:a {:href "/about"} "go to about page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

(defn counter []
  (let [seconds-elapsed (atom 0)
        off? (atom false)
        timeout (timeout #(swap! seconds-elapsed inc) 1000)]
    (fn []
      (let [off* @off?]
        ((if off* tt-clear! tt-do!) timeout)
        [:div.counter
         [:i {:on-click (fn [_] (swap! off? not))
              :style    {:color (if off* :green :red)}
              :class    (if off* "icon-play" "icon-pause")}]
         [:span.digit @seconds-elapsed]]))))

(defn counters-comp []
  (let [timers (atom {})]
    (fn []
      [:div.counters
       [:div.new {:on-click (fn [_] (swap! timers assoc (gensym) (counter)))} "new counter"]
       (for [[id t] (sort @timers)]
         ^{:key id}
         [:div
          [t]
          [:span {:on-click (fn [_] (swap! timers dissoc id))}
           [:i.icon-cancel]]])])))

(defn counters []
  [counters-comp])

;; ----------------------------------------------------------------------
;; App1

;; requests -----

(defn get-volumes! [state]
  (let [{:keys [plot span on]} @state
        now (now)]
    (request {:section  "statistics/v1/volume"
              :params   {"granularity" (:unit plot)
                         "timeFrom"    (ago (delta-t span))}
              :callback (fn [r]
                          (let [val (mapv #(reduce (fn [[t x] [_ y]] [t (+ x y)]) (first %) (next %))
                                          (partition (:val plot)
                                                     (-> r :body :data :messages)))]
                            (reset-in! state [:xs] (if on (conj (vec (next val)) [now 0]) val))))})))

(defn volumes-refresh! [state last-step]
  (request
    {:section  "statistics/v1/volume"
     :params   {"timeFrom" (time->str last-step)}
     :callback (fn [r]
                 (swap-in! state
                           [:xs]
                           #(conj (vec (butlast %)) (-> r :body :data :messages first))))}))

(defn get-messages [state from to]
  (reset-in! state [:fetching-messages] true)
  (let [from* (time->str from)
        to* (time->str to)]
    (request
      {:section  "content/v1/messages"
       :params   {"limit"    200
                  "timeFrom" from*
                  "timeTo"   to*}
       :callback (fn [r]
                   (swap! state
                          assoc
                          :fetching-messages
                          false
                          :messages
                          {:xs   (-> r :body :data)
                           :from from*
                           :to   to*}))})))

(defn get-last3-messages [state]
  (request
    {:section  "content/v1/messages"
     :params   {"limit" 3
                "sort"  "[\"pub_date_epoch_ms:desc\"]"}
     :callback (fn [r]
                 (swap! state
                        assoc
                        :last-messages
                        (-> r :body :data)))}))

;; comps ------

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

(defn messages-list [state]
  (let [ms (:messages @state)]
    [:div.messages-list
     [:div.head (if ms (str "tweets from " (time-str->hour-str (:from ms)) " to " (time-str->hour-str (:to ms)))
                       "lastest tweets")
      (when ms [:i.icon-cancel {:on-click (fn [_] (swap! state assoc :messages nil))}])]

     (for [x (if ms (:xs ms) (:last-messages @state))]
       ^{:key (gensym)}
       [:div.message
        [:div.author (:author x)]
        [:div.text (:text x)]])]))

;; main ------

(defn app1-comp [state]
  (let [state (atom state)
        last-step (cljs.core/atom nil)
        inter (cljs.core/atom nil)]
    (get-volumes! state)
    (fn []
      (let [{:keys [height plot
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
                    (js/setInterval (fn [] (volumes-refresh! state @last-step)
                                      (get-last3-messages state))
                                    (time/in-millis (delta-t rate))))))

        [:div.app1

         [:div.topbar
          [:i
           {:on-click (fn [_] (swap-in! state [:on] not))
            :style    {:float :left
                       :color (if on c2 c1)}
            :class    (if on "icon-pause" "icon-play")}]
          [:i.icon-cog
           {:class    (if show-options "icon-cancel" "icon-cog")
            :on-click (fn [_] (swap-in! state [:show-options] not))}]]
         (when show-options
           [:div.options
            (delta-t-comp state "division" plot :plot)
            (delta-t-comp state "total time" span :span)
            (delta-t-comp state "refresh rate" rate :rate)
            (color-picker "color 1" c1 (fn [v] (reset-in! state [:colors 0] v)))
            (color-picker "color 2" c2 (fn [v] (reset-in! state [:colors 1] v)))])

         [:div.chart
          {:style {:height (str height "px")}}
          (let [max (apply max (map second xs))]
            (for [[t x] xs
                  :let [rat (/ x max)
                        t (str->time t)]]
              ^{:key (gensym)}
              [:div.plot
               {:on-click (fn [_] (get-messages state t (time/plus t (delta-t plot))))
                :style    {:height           (str (int (+ 10 (* (- height 10) rat))) "px")
                           :background-color (rgb->str (blend c1 c2 rat))}}]))]

         (if fetching-messages
           [:div.messages-list [:div.head "fetching messages..."]]
           [messages-list state])]))))

(defn app1 []
  [app1-comp
   {:height       400
    :rate         {:unit "second" :val 2}
    :plot         {:unit "second" :val 20}
    :span         {:unit "minute" :val 15}
    :on           true
    :colors       [(gc/color-name->hex :lightskyblue)
                   (gc/color-name->hex :lightcoral)]
    :show-options false}])

;; charts -----------------------

; helpers --

(def prepend (comp vec cons))

(defn time-formatter [iso-str]
  (subs iso-str 0 19))

(defn format-columns [data label]
  [(prepend label (map second data))
   (prepend "x" (map (comp time-formatter first) data))])

(comment
  (time-formatter "2016-01-24T05:26:08.357Z")
  (format-columns [["2016-01-24T05:26:08.357Z" 1] ["2016-01-24T05:26:09.357Z" 1]]
                  "minute"))

(defn draw! [ref]
  (swap! ref
         assoc
         :var (.generate js/c3 (clj->js (select-keys @ref [:bindto :data :axis])))
         :drawn true))

(defn load! [ref]
  (.load (:var @ref) (clj->js {:columns (-> @ref :data :columns)})))

; comps ----

(defn graph-comp
  [{:keys [id sub span format title label type rate] :as state}]
  (let [i (cljs.core/atom nil)
        graph (atom
                {:bindto (str "#" id)
                 :data   {:x       "x"
                          :type    type
                          :columns [["x"] [label]]
                          :xFormat "%Y-%m-%dT%H:%M:%S"}
                 :axis   {:x {:type "timeseries" :tick {:format format}}}
                 :drawn false})
        req (fn []
              (request
                {:section  "statistics/v1/volume"
                 :params   {"granularity" (:unit sub)
                            "timeFrom"    (time->str (time/minus (time/now) (delta-t span)))}
                 :callback (fn [r]
                             (reset-in! graph
                                        [:data :columns]
                                        (format-columns (map #(reduce (fn [[t n] [_ m]] [t (+ n m)]) (first %) (next %))
                                                             (partition (:val sub) (-> r :body :data :messages)))
                                                        label))
                             (if (:drawn @graph)
                               (load! graph)
                               (draw! graph)))}))]
    (reagent/create-class
      {:component-did-mount
       (fn []
         (req)
         (reset! i (js/setInterval req (time/in-millis (delta-t sub)))))
       :component-will-unmount
       (fn []
         (js/clearInterval @i))
       :reagent-render
       (fn []
         [:div.graph-wrap
          [:div.title title]
          [:div.graph {:id id}]])})))

(defn volume-charts []
  [:div.graphs
   [:h2 "Tweet Volume"]
   [graph-comp
    {:id "last-minute-volumes"
     :title "last minute"
     :label "volume"
     :type "area-spline"
     :sub {:val 5 :unit "second"}
     :span {:val 1 :unit "minute"}
     :format "%H:%M:%S"}]
   [graph-comp
    {:id "last-hour-volumes"
     :title "last hour"
     :label "volume"
     :type "area-spline"
     :sub {:val 2 :unit "minute"}
     :span {:val 1 :unit "hour"}
     :format "%H:%M:%S"}]
   [graph-comp
    {:id "last-day-volumes"
     :title "last day"
     :label "volume"
     :type "area-spline"
     :sub {:val 1 :unit "hour"}
     :span {:val 1 :unit "day"}
     :format "%Hh"}]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
                    (session/put! :current-page #'home-page))

(secretary/defroute "/#/counters" []
                    (session/put! :current-page #'counters))

(secretary/defroute "/#/app1" []
                    (session/put! :current-page #'app1))

(secretary/defroute "/#/volume" []
                    (session/put! :current-page #'volume-charts))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (mount-root))

(init!)

