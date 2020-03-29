(ns appliedsciencestudio.covid19-clj-viz.south-america
  "Visualization of coronavirus situation in South America
  Contributed by Yuliana Apaza and Paula Asto."
  (:require [clojure.data.csv :as csv]
            [appliedsciencestudio.covid19-clj-viz.sources.johns-hopkins :as jh]
            [clojure.set :refer [rename-keys]]
            [jsonista.core :as json]
            [oz.core :as oz]))

(def southamerica-cases
  "Current number of COVID19 cases in South America, by countries"
  (->> jh/confirmed
       (map #(select-keys % [:country-region "2020-03-28"]))
       (filter (comp #{"Peru" "Brazil" "Argentina"
                       "Chile" "Bolivia" "Colombia"
                       "Ecuador" "Uruguay" "Paraguay" "Venezuela"
                       "Suriname" "Guyana"} :country-region))
       (map #(rename-keys % {"2020-03-28" :cases}))))

(def peru-cases
  "Current number of COVID19 cases in Peru, by regions"
  (->> (csv/read-csv (slurp "resources/peru.covid19cases-march28.csv"))))

(def south-america-geojson-with-data
  (update (json/read-value (java.io.File. "resources/public/public/data/southamerica.geo.json")
                           (json/object-mapper {:decode-key-fn true}))
          :features
          (fn [features]
            (mapv (fn [feature]
                    (let [cases (:cases (first (filter (comp #{(:geounit (:properties feature))} :country-region) southamerica-cases)))]
                      (assoc feature
                             :Country (:geounit (:properties feature))
                             :Cases cases)))
                  features))))

(def peru-geojson-with-data
  (update (json/read-value (java.io.File. "resources/public/public/data/peru-regions.geo.json")
                           (json/object-mapper {:decode-key-fn true}))
          :features
          (fn [features]
            (mapv (fn [feature]
                    (let [cases (nth (first (filter (comp #{(:NOMBDEP (:properties feature))} first) peru-cases)) 1)]
                      (assoc feature
                             :Region (:NOMBDEP (:properties feature))
                             :Cases (get peru-cases (:NOMBDEP (:properties feature)) cases))))
                  features))))

(oz/start-server! 8082)

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
             :fontSize 16
             :anchor "middle"}}))

(def map-dimensions
  {:width 550 :height 700})

(oz/view!
 (merge-with merge oz-config map-dimensions
             {:title {:text "COVID-19 cases in South America by country"}
              :data {:name "south-america"
                     :values south-america-geojson-with-data
                     :format {:property "features"}}
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :encoding {:color {:field "Cases"
                                 :type "quantitative"
                                 :scale {:range ["#fde5d9" "#a41e23"]}}
                         :tooltip [{:field "Country" :type "nominal"}
                                   {:field "Cases" :type "quantitative"}]}
              :selection {:highlight {:on "mouseover" :type "single"}}}))

(oz/view!
 (merge-with merge oz-config map-dimensions
             {:title {:text "COVID-19 cases in Peru by Regions"}
              :data {:name "peru"
                     :values peru-geojson-with-data
                     :format {:property "features"}}
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :encoding {:color {:field "Cases"
                                 :type "quantitative"
                                 :condition {:test  "datum['Cases'] == 0" :value "#F3F3F3"}}
                         :tooltip [{:field "Region" :type "nominal"}
                                   {:field "Cases" :type  "quantitative"}]}
              :selection {:highlight {:on "mouseover" :type "single"}}}))