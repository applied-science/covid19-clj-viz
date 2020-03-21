(ns appliedsciencestudio.covid19-clj-viz.viz-south-america
  (:require [appliedsciencestudio.covid19-clj-viz.south-america :as southamerica]
            [jsonista.core :as json]
            [oz.core :as oz]))

(oz/start-server! 8082)

(def south-america-geojson-with-data
  (update (json/read-value (java.io.File. "resources/public/public/data/south-america.json")
                           (json/object-mapper {:decode-key-fn true}))
          :features
          (fn [features]
            (mapv (fn [feature]
                    (let [cases (nth (first (filter (comp #{(:geounit (:properties feature))} second)
                                                       southamerica/cases)) 2)]
                      (assoc feature
                      :Name (:geounit (:properties feature))
                      :Cases (get southamerica/cases (:geounit (:properties feature)) cases))
                      ))
                  features ))))

(def peru-geojson-with-data
  (update (json/read-value (java.io.File. "resources/public/public/data/peru.geo.json")
                           (json/object-mapper {:decode-key-fn true}))
          :features
          (fn [features]
            (mapv (fn [feature]
                    (let [cases ( first (first (filter (comp #{(:NOMBDEP (:properties feature))} second)
                                                       southamerica/peru-cases)))]
                      (assoc feature
                      :Name (:NOMBDEP (:properties feature))
                      :Cases (get southamerica/peru-cases (:NOMBDEP (:properties feature)) cases))
                      ))
                  features ))))

(def oz-config
  "Default settings for Oz visualizations"
  (let [font "IBM Plex Mono"]
    {:config {:style {:cell {:stroke "transparent"}}
              :legend {:labelFont font
                       :labelFontSize 12
                       :titleFont "IBM Plex Mono"
                       :gradientThickness 40}
              :axis {:labelFont font
                     :titleFont font
                     :titleFontSize 20}}
     :title {:font "IBM Plex Sans"
             :fontSize 14
             :anchor "middle"}}))

(def south-america-dimensions
  {:width 550 :height 700})

;;;; Geographic visualization of cases in each South America country state
(oz/view!
 (merge-with merge oz-config south-america-dimensions
             {:title {:text "COVID-19 cases in South America by country"}
              :data {:name "south-america"
                     :values south-america-geojson-with-data
                     :format {:property "features"}},
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :encoding {:color {:field "Cases",
                                 :type "quantitative"
                                 :scale {:range ["#fde5d9" "#a41e23"]}}
                         :tooltip [
                                   {:field "Cases" :type "quantitative"}]}
              :selection {:highlight {:on "mouseover" :type "single"}}}))