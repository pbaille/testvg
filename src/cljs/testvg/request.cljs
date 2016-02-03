(ns testvg.request
  (:require [cljs.core.async :as async]
            [testvg.utils :as u]
            [cljs-http.client :as http]))

(def base-url "http://api.vigiglobe.com/api/")

(defn default-params []
  {"project_id" "vgteam-TV_Shows"
   "timeTo"     (u/now-str)})

(defn request [{:keys [section params callback]}]
  (async/take! (http/get (str base-url section)
                         {:with-credentials? false
                          :query-params      (merge (default-params) params)})
               (or callback identity)))

;; specific -------------

(defn get-volumes! [state]
  (let [{:keys [plot span on]} @state
        now (u/now-str)]
    (request {:section  "statistics/v1/volume"
              :params   {"granularity" (:unit plot)
                         "timeFrom"    (u/ago-str (u/delta-t span))}
              :callback (fn [r]
                          (let [val (mapv #(reduce (fn [[t x] [_ y]] [t (+ x y)]) (first %) (next %))
                                          (partition (:val plot)
                                                     (-> r :body :data :messages)))]
                            (u/reset-in! state [:xs] (if on (conj (vec (next val)) [now 0]) val))))})))

(defn volumes-refresh! [state last-step]
  (request
    {:section  "statistics/v1/volume"
     :params   {"timeFrom" (u/time->str last-step)}
     :callback (fn [r]
                 (u/swap-in! state
                           [:xs]
                           #(conj (vec (butlast %)) (-> r :body :data :messages first))))}))

(defn get-messages [state from to]
  (u/reset-in! state [:fetching-messages] true)
  (let [from* (u/time->str from)
        to* (u/time->str to)]
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
     :params   {"limit"    3
                "sort"     "[\"pub_date_epoch_ms:desc\"]"
                "timeFrom" (u/ago-str (u/delta-t (:plot @state)))
                "timeTo"   (u/now-str)}
     :callback (fn [r]
                 (swap! state
                        assoc
                        :last-messages
                        (-> r :body :data)))}))