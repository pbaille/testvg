(ns testvg.prod
  (:require [testvg.core-old :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
