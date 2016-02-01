(ns testvg.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            #_[testvg.middleware :refer [wrap-middleware]]
            [environ.core :refer [env]]))

(def mount-target
  [:div
   [:div.nav
    [:a {:href "/"} "home"]
    [:a.exo {:href "/#/counters"} "counters"]
    [:a.exo {:href "/#/volume"} "volume"]
    [:a.exo {:href "/#/plots"} "plots"]
    [:a.exo {:href "/#/cloud"} "cloud"]]
   [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]]])

(def loading-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css "css/site.css")
     (include-css "fontello/css/fontello.css")
     (include-css "css/style.css")
     (include-css "css/c3.min.css")]
    [:body
     mount-target
     (include-js "js/main.js")
     (include-js "https://cdn.rawgit.com/pbaille/f09bdcad7586e08f6bed/raw/7729a23eeb2beee5f91204c73000dfcd564702e7/d3-cloud.js")]]))


(defroutes routes
  (GET "/" [] loading-page)
  (GET "/about" [] loading-page)
  
  (resources "/")
  (not-found "Not Found"))

(def app #'routes)
