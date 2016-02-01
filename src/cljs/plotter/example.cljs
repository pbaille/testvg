(ns plotter.example
  (:require cljsjs.d3
            [plotter.core :refer [make-plotter =>]]
            [reagent.core :as reagent :refer [atom]]))

(defn styles [m]
  (map (fn [[k v]] [:style [(name k) v]]) m))

(defn attrs [m]
  (map (fn [[k v]] [:attr [(name k) v]]) m))

(defn prop? [x]
  (and (vector? x) (keyword? (first x))))

(defn flat-props [xs]
  (mapcat #(if (prop? %) [%] (flat-props %)) xs))

(defn >> [x & xs]
  (=> x (flat-props xs)))

;; main -----------

(defn rd3 [{:keys [id init upd data attrs]}]
  (let [node (atom nil)
        data (if (implements? IDeref data) data (cljs.core/atom data))
        base (cljs.core/atom nil)
        k (keyword (gensym))
        _ (add-watch data k (fn [_ x _ _] (upd @base x)))]
    (reagent/create-class
      {:component-did-update
       (fn []
         (reset! base (>> (make-plotter js/d3) (cons [:select (str "#" id)] init)))
         (upd @base data))
       :component-did-mount
       (fn [this]
         ;; This will trigger a re-render of the component.
         (reset! node (reagent/dom-node this)))
       :reagent-render
       (fn []
         @node
         [:div (merge {:id id} (if (fn? attrs) (attrs data) attrs))])})))

;; rd3 usage examples --

(defn alphabet []
  [rd3
   {:id
    "foo"

    :init
    [[:append "svg"]
     (attrs {:width 600 :height 200})
     [:append "g"]]

    :upd
    (fn [base data]
      (let [base (>> base [:selectAll "text"] [:data [(clj->js @data) identity]])]
        (>> base [:enter] [:append "text"] (attrs {:dy 100}))
        (>> base [:text identity] (attrs {:x (fn [d i] (* 20 i))}))
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


(defn indexed [c] (mapv vector (range) c))

(defn circles [{:keys [width height n data]}]
  (let [offset (/ width (inc n))]
    [rd3
     {:id
      "foo"

      :init
      [[:append "svg"]
       (attrs {:width 600 :height 200})]

      :upd
      (fn [base data]
        (let [base (>> base [:selectAll "circle"] [:data [(clj->js @data) identity]])]
          (>> base [:enter] [:append "circle"])
          (>> base (attrs {:cx (fn [[i d]] (+ offset (* offset i)))
                           :cy (/ height 2)
                           :r  (fn [[_ d]] (* d 10))}))
          (>> base [:exit] [:remove])))

      :data
      data

      :attrs
      (fn [data]
        {:on-click #(reset! data (indexed (shuffle (range 1 6))))})}]))

(defn c1 []
  [circles {:width 600 :height 200 :n 5 :data (indexed [2 4 5 3 1])}])




