(defproject testvg "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [ring-server "0.4.0"]
                 [reagent "0.5.1"
                  :exclusions [org.clojure/tools.reader]]
                 [reagent-forms "0.5.13"]
                 [reagent-utils "0.1.7"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [compojure "1.4.0"]
                 [hiccup "1.0.5"]
                 [environ "1.0.1"]
                 [org.clojure/clojurescript "1.7.228" :scope "provided"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.1.6"
                  :exclusions [org.clojure/tools.reader]]
                 [clj-time "0.11.0"]                        ;in order to avoid a recurent bug with ring
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [cljs-http "0.1.39"]
                 [garden "1.3.0"]
                 [cljsjs/c3 "0.4.10-0"]
                 [figwheel-sidecar "0.5.0"]]

  :plugins [[lein-environ "1.0.1"]
            [lein-cljsbuild "1.1.1"]
            [lein-asset-minifier "0.2.4"
             :exclusions [org.clojure/clojure]]]

  :ring {:handler testvg.handler/app
         :uberwar-name "testvg.war"}

  :min-lein-version "2.5.0"

  :uberjar-name "testvg.jar"

  :main testvg.server

  :clean-targets ^{:protect false} [:target-path
                                    [:cljsbuild :builds :app :compiler :output-dir]
                                    [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources"])
