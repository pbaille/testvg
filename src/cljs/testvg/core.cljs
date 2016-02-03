(ns testvg.core
  (:require [reagent.core :as reagent]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [testvg.components.counters :refer [counters]]
            [testvg.components.plots :refer [plots]]
            [testvg.components.volumes :refer [volume-charts]]
            [testvg.components.cloud :refer [wcloud]]))

;; -------------------------
;; Views

(defn home-page []
  [:div.home [:h1 "Welcome!"]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
                    (session/put! :current-page #'home-page))

(secretary/defroute "/#/counters" []
                    (session/put! :current-page #'counters))

(secretary/defroute "/#/plots" []
                    (session/put! :current-page #'plots))

(secretary/defroute "/#/volume" []
                    (session/put! :current-page #'volume-charts))

(secretary/defroute "/#/cloud" []
                    (session/put! :current-page #'wcloud))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (mount-root))

(init!)
