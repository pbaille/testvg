(ns d3.word-cloud
  (:require cljsjs.d3
            [d3.utils :as u :refer [p >> *> js>]]))

(defn word-cloud [{:keys [selector width height rotate font-size-range handler]}]
  (let [fill (.category20 u/scale)
        svg (*> [:select selector]
                [:append "svg"]
                (u/attrs {:width width :height height})
                [:append "g"]
                (u/translate (/ width 2) (/ height 2)))
        draw (fn [words]
               (let [cl (>> svg
                            [:selectAll "g text"]
                            [:data [words #(.-text %) #_(js> [(.-frequency %) (.-text %)])]])]
                 (>> cl
                     [:enter]
                     [:append "text"]
                     (u/styles {:font-family "Impact"
                                :fill        #(fill %2)})
                     (u/attrs {:text-anchor "middle"
                               :font-size   1})
                     [:text #(.-text %)]
                     [:on ["click" (fn [d] (handler d))]])

                 (>> cl
                     [:transition]
                     [:duration 600]
                     (u/styles {:font-size    #(str (.-frequency %) "px")
                                :fill-opacity 1})
                     [:attr ["transform" #(str "translate(" (.-x %) "," (.-y %)
                                               ") rotate(" (.-rotate %) ")")]])
                 (>> cl
                     [:exit]
                     [:transition]
                     [:duration 200]
                     [:style ["fill-opacity" 0.000001]]
                     [:attr ["font-size" 1]]
                     [:remove])))]
    (fn [ws]
      (let [freqs (mapv :frequency ws)
            fs-scale (.. u/scale linear
                         (domain (js> [(apply min freqs)(apply max freqs)]))
                         (range (js> font-size-range)))
            ws (mapv (fn [x] (update-in x [:frequency] fs-scale)) ws)]
        (.. (.. js/d3 -layout cloud)
            (size (js> [width height]))
            (words (js> ws))
            (padding 5)
            (rotate rotate)
            (font "Impact")
            (fontSize (fn [d] (.-frequency d)))
            (on "end" draw)
            start)))))


(comment
  (do

    (clear-svg!)
    (let [words ["You don't know about me without you have read a book called The Adventures of Tom Sawyer but that ain't no matter.",
                 "The boy with fair hair lowered himself down the last few feet of rock and began to pick his way toward the lagoon.",
                 "When Mr. Bilbo Baggins of Bag End announced that he would shortly be celebrating his eleventy-first birthday with a party of special magnificence, there was much talk and excitement in Hobbiton.",
                 "It was inevitable: the scent of bitter almonds always reminded him of the fate of unrequited love."]
          wgen #(mapv (fn [t] {:text      t
                               :frequency (+ 10 (rand-nth [10 30 50]))})
                      (clojure.string/split (rand-nth words) #" "))
          wc (word-cloud {:selector        "#app"
                          :width           500
                          :height          500
                          :font-size-range [10 50]
                          :rotate          (fn [] (rand-nth [0 90 -90]))})]
      (js/setInterval #(wc (wgen)) 2000))
    ))
