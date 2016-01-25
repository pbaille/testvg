(ns testvg.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [testvg.middleware :refer [wrap-middleware]]
            [environ.core :refer [env]]))

(def mount-target
  [:div
   [:div.links
    [:a {:href "/"} "home"]
    [:a {:href "/about"} "about"]
    [:a.exo {:href "/#/test1"} "test1"]
    [:a.exo {:href "/#/test2"} "test2"]
    [:a.exo {:href "/#/test3"} "test3"]
    [:a.exo {:href "/#/bonus"} "bonus"]
    [:a.exo {:href "/#/spline"} "spline charts"]
    [:a.exo {:href "/#/extra"} "extra"]]
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
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))
     (include-css "fontello/css/fontello.css")
     (include-css "css/style.css")
     (include-css "css/c3.min.css")]
    [:body
     mount-target
     (include-js "js/app.js")]]))


(defroutes routes
  (GET "/" [] loading-page)
  (GET "/about" [] loading-page)
  
  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
