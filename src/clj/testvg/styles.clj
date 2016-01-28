(ns testvg.styles
  [:require [garden.core :refer :all]
            [garden.def :refer [defstylesheet defstyles]]])

(defn graph [id color]
  [id
   [:.c3-line-volume {:stroke (str color "!important")}]
   [:.c3-area {:fill (str color "!important")}]
   [:.c3-circles-volume {:stroke (str color "!important")}
    [:.c3-circle {:fill (str color "!important")}]]])


(defstyles screen
           {:output-to "resources/public/css/style.css"}

           [:body {:margin  "0 auto"
                   :padding 0}]

           [:.nav
            {:height :70px
             :padding "0 30px"
             :background-color :lightgrey}
            [:a {:line-height :70px
                 :padding "0 5px"
                 :float   :left
                 :color   :white
                 :font-size :18px
                 :font-weight :bold}
             [:&.exo {:float :right}]]]

           [:.counters
            {:padding :30px}
            [:.new {:display          :inline-block
                    :padding          :10px
                    :background-color :lightskyblue
                    :color            :white
                    :margin-bottom :20px}]
            [:.counter {:display          :inline-block
                        :padding          :10px
                        :background-color :FAFAFA
                        :margin           "10px 0"}
             [:.digit {:font-size :30px}]]
            [:i.icon-cancel
             {:float :none}]]


           [:i
            {:color     :grey
             :font-size :30px
             :opacity   0.7}
            [:&:hover
             {:opacity 1}]
            [:&.play-toggle {:float :left}]
            [:&.icon-cog {:float :right}]
            [:&.icon-cancel {:position :relative
                             :float    :right}]]

           [:.app1
            {:padding :30px}

            [:.topbar
             {:height :60px}]

            [:.delta-t-comp
             [:input
              {:width      :50px
               :text-align :right
               :border     :none
               :font-size  :12px}]]

            [:.options
             {:background-color :FAFAFA
              :margin           :10px
              :padding          :20px
              :border-radius    :5px
              :margin-bottom    :20px}

             [:select
              {:border    :none
               :font-size :14px}
              [:&:focus
               {:outline :none}]
              [:&.color
               {:width :135px}]]

             [:.label
              {:display       :inline-block
               :width         :45%
               :text-align    :right
               :padding-right :10px
               :color         :grey}]]

            [:.chart {:margin-bottom :20px
                      :width       :100%
                      :display     :flex
                      :felx-wrap   :nowrap
                      :align-items :flex-end}
             [:.plot {:flex "1 1 auto"
                      :border     "2px solid white"}
              [:&:hover {:flex-grow 1.5
                         :opacity 0.7}]]]

            [:.messages-list
             [:.head
              {:text-align :center
               :color      :grey
               :font-size  :25px
               :padding    :10px}]
             [:i.icon-cancel
             {:color     :grey
              :font-size :25px}]
             [:.message
              {:background-color :FAFAFA
               :margin           :10px
               :padding          :20px
               :border-radius    :5px
               :margin-bottom    :20px}
              [:.author
               {:font-size      :22px
                :color          :lightskyblue
                :padding-bottom :15px}]]]]



           [:.graphs
            {:padding "0 30px"}
            [:h2 {:padding :20px
                  :text-align :center
                  :color      :grey}]
            [:.graph-wrap [:.title {:color   :darkgrey
                                    :padding :10px}]
             [:.c3-legend-item {:display :none}]
             (graph :#last-minute-volumes "lightsalmon")
             (graph :#last-hour-volumes "darkviolet")
             (graph :#last-day-volumes "yellowgreen")]])
