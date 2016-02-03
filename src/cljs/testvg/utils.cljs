(ns testvg.utils
  (:require
            [cljs.core.async :as async]
            [cljs-time.core :as time]
            [cljs-time.format :as tformat]
            [garden.color :as gc]))

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

(defn now []
  (time/minus (time/now) (time/days 3)))

(defn now-str []
  (time->str (now)))

(defn ago [delta-t]
  (time/minus (now) delta-t))

(defn ago-str [delta-t]
  (time->str (ago delta-t)))

(def str->time-unit-fn
  {"minute" time/minutes
   "second" time/seconds
   "hour"   time/hours
   "day"    time/days})

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




