(ns d3.utils
  (:require
    cljsjs.d3
    [plotter.core :refer [make-plotter =>]]
    [reagent.core :as reagent :refer [atom]]))

(def js> clj->js)

(def p make-plotter)

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

(defn *> [& xs]
  (>> (make-plotter js/d3) xs))

;; little helpers --------------

(defn translate [a b]
  [:attr ["transform" (str "translate(" a "," b ")")]])

(defn translate-rotate [a b c]
  [:attr ["transform" (str "translate(" a "," b ")rotate(" c ")")]])

(defn lscale [d r]
  (.. (.-scale js/d3) linear (domain (js> d)) (range (js> r))))

;;reagent wrapper --------------

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

;; D3 VARS -------------------------------------------------------------------

;; svg --------------------------

(def svg (.-svg js/d3))

(def arc (.. js/d3 -svg -arc))

;; geom -------------------------

(def polygon (.. js/d3 -geom -polygon))
(def voronoi (.. js/d3 -geom -voronoi))

;; scale ------------------------

(def scale (.-scale js/d3))

(def category10 (.. js/d3 -scale -category10))
(def category20 (.. js/d3 -scale -category20))
(def category20b (.. js/d3 -scale -category20b))
(def category20c (.. js/d3 -scale -category20c))

;; layout ------------------------

(def layout (.-layout js/d3))

#_(def cloud (.. js/d3 -layout -cloud))

;; other -------------------------

(defn pack [] (.pack layout))

(def json (.-json js/d3))

(def timer (.. js/d3 -timer))


