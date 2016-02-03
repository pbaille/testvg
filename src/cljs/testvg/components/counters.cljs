(ns testvg.components.counters
  (:require [testvg.utils :as u]
            [reagent.core :as r]))

(defn counter []
  (let [seconds-elapsed (r/atom 0)
        off? (r/atom false)
        timeout (u/timeout #(swap! seconds-elapsed inc) 1000)]
    (fn []
      (let [off* @off?]
        ((if off* u/tt-clear! u/tt-do!) timeout)
        [:div.counter
         [:i {:on-click (fn [_] (swap! off? not))
              :style    {:color (if off* :green :red)}
              :class    (if off* "icon-play" "icon-pause")}]
         [:span.digit @seconds-elapsed]]))))

(defn counters-comp []
  (let [timers (r/atom {})]
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
