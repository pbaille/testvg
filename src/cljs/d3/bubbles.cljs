(ns d3.bubbles
  (:require cljsjs.d3
            [d3.utils :as u :refer [>> *>]]))

#_(let [diameter 960
      format (*> [:format ",d"])
      color (u/category20c)

      bubbles (>> (u/pack)
                  [:sort nil]
                  [:size [diameter diameter]]
                  [:padding 1.5])

      svg (*> [:select "body"]
              [:append "svg"]
              (u/attrs {:class "bubble"
                        :width diameter
                        :height diameter}))

      classes (fn [root]
                (let [classes []
                      recurse (fn [name node]
                                (if (.children node)
                                  ()))]))])