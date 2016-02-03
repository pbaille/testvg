(ns testvg.components.cloud
  (:require cljsjs.d3
            [d3.utils :as d3u]
            [d3.word-cloud :refer [word-cloud]]
            [testvg.utils :as u]
            [testvg.request :as req]
            [reagent.core :as r]
            [cljs-time.core :as time]))

(defn wc-request [{:keys [kind span limit]} data]
  (req/request {:section  "statistics/v1/wordcloud"
            :params   {"limit"    limit
                       "kind"     kind
                       "timeFrom" (u/ago-str span)}
            :callback (fn [x]
                        (reset! data
                                (mapv (fn [[k v]] {:text      (str (when (= kind "hashtags") "#") (name k))
                                                   :frequency (second (first (:messages v)))})
                                      (-> x :body :data))))}))

(def wc-messages (r/atom {:xs [] :text nil}))

(defn get-wc-messages [{:keys [kind span limit text]}]
  (req/request {:section  "content/v1/messages"
            :params   {"limit"    limit
                       "sort"     "[\"pub_date_epoch_ms:desc\"]"
                       kind       (str "[\"" text "\"]")
                       "timeFrom" (u/ago-str span)}
            :callback (fn [x]
                        (swap! wc-messages assoc
                               :xs (-> x :body :data)
                               :text (str (when (= kind "hashtags") "#") (name text))))}))

(defn wcloud-comp [state]
  (let [node (r/atom nil)
        wc (atom nil)
        rot-scaler (.. d3u/scale linear (domain #js [0 1]) (range #js[-10 10]))
        state (atom state)
        data (atom nil)
        refresh-data (fn [] (wc-request @state data))]

    (refresh-data)
    (add-watch state :state (fn [_ x _ _] (refresh-data)))
    (add-watch data :data (fn [_ x _ _] (@wc @data)))

    (r/create-class
      {:component-did-update
       (fn []
         (if-not @wc
           (reset! wc (word-cloud
                        {:selector        "#wc-display"
                         :width           800
                         :height          500
                         :rotate          (fn [] (rot-scaler (rand)))
                         :font-size-range [20 50]
                         :handler (fn [d] (get-wc-messages
                                            (merge @state
                                                   {:limit 12
                                                    :text (if (= (:kind @state) "hashtags")
                                                            (apply str (next (.-text d)))
                                                            (.-text d))})))})))
         (@wc @data))
       :component-did-mount
       (fn [this] (reset! node (r/dom-node this)))
       :reagent-render
       (fn []
         @node
         [:div.word-cloud
          [:div.actions
           [:span {:on-click (fn [] (swap! state assoc :kind "hashtags"))} "hashtags"]
           [:span {:on-click (fn [] (swap! state assoc :kind "tokens"))} "tokens"]
           [:span.right {:on-click (fn [] (swap! state assoc :span (time/minutes 1)))} "minute"]
           [:span.right {:on-click (fn [] (swap! state assoc :span (time/hours 1)))} "hour"]
           [:span.right {:on-click (fn [] (swap! state assoc :span (time/days 1)))} "day"]]
          [:div#wc-display]])})))

(defn wc-messages-list []
  (let [{:keys [text xs]} @wc-messages]
    [:div.messages-list
     [:div.head
      {:style {:font-size :30px}}
      (if text text "click a word to see messages")
      (when text [:i.icon-cancel {:on-click (fn [_] (reset! wc-messages {:xs [] :text nil}))}])]

     (for [x xs
           :let [retweet_count (:retweet_count x)
                 favorite_count (:favorite_count x)
                 id (:id_str x)]]
       ^{:key (gensym)}
       [:div.message {:on-click (fn [] (.open js/window (str "https://twitter.com/statuses/" id) "_blank"))}
        [:div.author {:on-click (fn [] (.open js/window (str "https://twitter.com/" (:author x)) "_blank"))}
         (:author x)]
        #_[:img {:src (first (:images x))}]
        (when-let [fi (first (:images x))]
          [:div.image {:style {:background (str "url(" fi ")")}}])
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

(defn wcloud []
  [:div
   [wcloud-comp {:kind  "hashtags"
                 :limit 80
                 :span  (time/days 1)
                 :rate  (time/hours 1)}]
   [wc-messages-list]])
