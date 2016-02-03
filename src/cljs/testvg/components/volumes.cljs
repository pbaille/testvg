(ns testvg.components.volumes
  (:require cljsjs.c3
            [reagent.core :as r]
            [testvg.utils :as u]
            [testvg.request :as req]
            [cljs-time.core :as time]))

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
  (let [i (atom nil)
        graph (r/atom
                {:bindto (str "#" id)
                 :data   {:x       "x"
                          :type    type
                          :columns [["x"] [label]]
                          :xFormat "%Y-%m-%dT%H:%M:%S"}
                 :axis   {:x {:type "timeseries" :tick {:format format}}}
                 :drawn  false})
        req (fn []
              (req/request
                {:section  "statistics/v1/volume"
                 :params   {"granularity" (:unit sub)
                            "timeFrom"    (u/ago-str (u/delta-t span))}
                 :callback (fn [r]
                             (u/reset-in! graph
                                        [:data :columns]
                                        (format-columns (map #(reduce (fn [[t n] [_ m]] [t (+ n m)]) (first %) (next %))
                                                             (partition (:val sub) (-> r :body :data :messages)))
                                                        label))
                             (if (:drawn @graph)
                               (load! graph)
                               (draw! graph)))}))]
    (r/create-class
      {:component-did-mount
       (fn []
         (req)
         (reset! i (js/setInterval req (time/in-millis (u/delta-t sub)))))
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
    {:id     "last-minute-volumes"
     :title  "last minute"
     :label  "volume"
     :type   "area-spline"
     :sub    {:val 5 :unit "second"}
     :span   {:val 1 :unit "minute"}
     :format "%H:%M:%S"}]
   [graph-comp
    {:id     "last-hour-volumes"
     :title  "last hour"
     :label  "volume"
     :type   "area-spline"
     :sub    {:val 2 :unit "minute"}
     :span   {:val 1 :unit "hour"}
     :format "%H:%M:%S"}]
   [graph-comp
    {:id     "last-day-volumes"
     :title  "last day"
     :label  "volume"
     :type   "area-spline"
     :sub    {:val 1 :unit "hour"}
     :span   {:val 1 :unit "day"}
     :format "%Hh"}]])

