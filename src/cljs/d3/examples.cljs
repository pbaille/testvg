(ns d3.examples
  (:require [d3.utils :as u :refer [rd3 >>]]))

(defn indexed [c] (mapv vector (range) c))

(defn alphabet []
  [rd3
   {:id
    "foo"

    :init
    [[:append "svg"]
     (u/attrs {:width 600 :height 200})
     [:append "g"]]

    :upd
    (fn [base data]
      (let [base (>> base [:selectAll "text"] [:data [(clj->js @data) identity]])]
        (>> base [:enter] [:append "text"] (u/attrs {:dy 100}))
        (>> base [:text identity] (u/attrs {:x (fn [d i] (* 20 i))}))
        (>> base [:exit] [:remove])))

    :data
    (vec "abcdefghijklmnopqrstuwvxyz")

    :attrs

    (fn [data]
      {:on-click #(reset! data
                          (-> (vec "abcdefghijklmnopqrstuwvxyz")
                              shuffle
                              (subvec (rand-int 26))
                              sort
                              vec))})}])

(defn circles [{:keys [width height n data]}]
  (let [offset (/ width (inc n))]
    [rd3
     {:id
      "foo"

      :init
      [[:append "svg"]
       (u/attrs {:width 600 :height 200})]

      :upd
      (fn [base data]
        (let [base (>> base [:selectAll "circle"] [:data [(clj->js @data) identity]])]
          (>> base [:enter] [:append "circle"])
          (>> base (u/attrs {:cx (fn [[i d]] (+ offset (* offset i)))
                             :cy (/ height 2)
                             :r  (fn [[_ d]] (* d 10))}))
          (>> base [:exit] [:remove])))

      :data
      data

      :attrs
      (fn [data]
        {:on-click #(reset! data (indexed (shuffle (range 1 6))))})}]))

(defn c1 []
  [circles {:width 600
            :height 200
            :n 5
            :data (indexed [2 4 5 3 1])}])




