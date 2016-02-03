(ns testvg.components.plots
  (:require [testvg.utils :as u]
            [testvg.request :as req]
            [reagent.core :as r]
            [garden.color :as gc]
            [cljs-time.core :as time]))

;; subs ------

(defn delta-t-comp [ref label {:keys [val unit]} key]
  (let [cb (fn [field e]
             (u/reset-in! ref
                        [key field]
                        ((if (= field :val) int identity)
                          (.. e -target -value)))
             (req/get-volumes! ref))]
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
     [:div.head (if ms (str "tweets from " (u/time-str->hour-str (:from ms)) " to " (u/time-str->hour-str (:to ms)))
                       "lastest tweets")
      (when ms [:i.icon-cancel {:on-click (fn [_] (swap! state assoc :messages nil))}])]

     (for [x (if ms (:xs ms) (:last-messages @state))
           :let [retweet_count (:retweet_count x)
                 favorite_count (:favorite_count x)
                 id (:id_str x)]]
       ^{:key (gensym)}
       [:div.message {:on-click (fn [] (.open js/window (str "https://twitter.com/statuses/" id) "_blank"))}
        [:div.author {:on-click (fn [] (.open js/window (str "https://twitter.com/" (:author x)) "_blank"))}
         (:author x)]
        [:img {:src (first (:images x))}]
        [:div.text (:text x)]
        [:div.icons
         [:i.icon-heart {:on-click
                         (fn [e]
                           (.open js/window (str "https://twitter.com/intent/like?tweet_id=" id) "_blank")
                           (.stopPropagation e))}]
         (when-not (zero? favorite_count) [:span favorite_count])
         [:i.icon-retweet {:on-click
                           (fn [e]
                             (.open js/window (str "https://twitter.com/intent/retweet?tweet_id=" id) "_blank")
                             (.stopPropagation e))}]
         (when-not (zero? retweet_count) [:span retweet_count])]])]))

;; main ------

(defn app1-comp [state]
  (let [state (r/atom state)
        last-step (atom nil)
        inter (atom nil)]
    (req/get-volumes! state)
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
                          (time/before? (time/plus @last-step (u/delta-t plot))
                                        (u/now)))]

            (if step?
              (do (reset! last-step (u/now))
                  (u/swap-in! state [:xs] #(vec (next (conj % [(u/now-str) 0]))))))
            (reset! inter
                    (js/setInterval (fn [] (req/volumes-refresh! state @last-step)
                                      (req/get-last3-messages state))
                                    (time/in-millis (u/delta-t rate))))))

        [:div.app1

         [:div.topbar
          [:i
           {:on-click (fn [_] (u/swap-in! state [:on] not))
            :style    {:float :left
                       :color (if on c2 c1)}
            :class    (if on "icon-pause" "icon-play")}]
          [:i.icon-cog
           {:class    (if show-options "icon-cancel" "icon-cog")
            :on-click (fn [_] (u/swap-in! state [:show-options] not))}]]
         (when show-options
           [:div.options
            (delta-t-comp state "division" plot :plot)
            (delta-t-comp state "total time" span :span)
            (delta-t-comp state "refresh rate" rate :rate)
            (color-picker "color 1" c1 (fn [v] (u/reset-in! state [:colors 0] v)))
            (color-picker "color 2" c2 (fn [v] (u/reset-in! state [:colors 1] v)))])

         [:div.chart
          {:style {:height (str height "px")}}
          (let [max (apply max (map second xs))]
            (for [[t x] xs
                  :let [rat (/ x max)
                        t (u/str->time t)]]
              ^{:key (gensym)}
              [:div.plot
               {:on-click (fn [_] (req/get-messages state t (time/plus t (u/delta-t plot))))
                :style    {:height           (str (int (+ 10 (* (- height 10) rat))) "px")
                           :background-color (u/rgb->str (u/blend c1 c2 rat))}}]))]

         (if fetching-messages
           [:div.messages-list [:div.head "fetching messages..."]]
           [messages-list state])]))))

(defn plots []
  [app1-comp
   {:height       400
    :rate         {:unit "second" :val 2}
    :plot         {:unit "second" :val 10}
    :span         {:unit "minute" :val 10}
    :on           true
    :colors       [(gc/color-name->hex :lightskyblue)
                   (gc/color-name->hex :lightcoral)]
    :show-options false}])
