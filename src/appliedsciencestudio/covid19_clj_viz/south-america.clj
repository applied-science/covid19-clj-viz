(ns appliedsciencestudio.covid19-clj-viz.south-america
  "Visualization of coronavirus situation in South America.
  
  Contributed by Yuliana Apaza and Paula Asto."
  (:require [appliedsciencestudio.covid19-clj-viz.sources.johns-hopkins :as jh]
            [appliedsciencestudio.covid19-clj-viz.common :refer [oz-config]]
            [meta-csv.core :as mcsv]
            [clojure.set :refer [rename-keys]]
            [jsonista.core :as json]
            [oz.core :as oz]))

(def southamerica-cases
  "Current number of COVID19 cases in South America, by countries"
  (let [date "2020-03-28"]
    (->> jh/confirmed
         (map #(select-keys % [:country-region date]))
         (filter (comp #{"Peru" "Brazil" "Argentina"
                         "Chile" "Bolivia" "Colombia"
                         "Ecuador" "Uruguay" "Paraguay" "Venezuela"
                         "Suriname" "Guyana"}
                       :country-region))
         (reduce (fn [m row]
                   (assoc m (:country-region row) (get row date 0)))
                 {}))))

(def peru-cases
  "Mapping from Peruvian region to number of cases (as of 28 March 2020)."
  (reduce (fn [m {:keys [region cases]}]
            (assoc m region cases))
          {}
          (mcsv/read-csv "resources/peru.covid19cases-march28.csv"
                         {:fields [:region :cases]})))

(def south-america-geojson-with-data
  (update (json/read-value (java.io.File. "resources/public/public/data/southamerica.geo.json")
                           (json/object-mapper {:decode-key-fn true}))
          :features
          (fn [features]
            (mapv (fn [feature]
                    (let [country (:geounit (:properties feature))
                          cases (get southamerica-cases country)]
                      (assoc feature
                             :Country country
                             :Cases cases)))
                  features))))

(def peru-geojson-with-data
  (update (json/read-value (java.io.File. "resources/public/public/data/peru-regions.geo.json")
                           (json/object-mapper {:decode-key-fn true}))
          :features
          (fn [features]
            (mapv (fn [feature]
                    (let [region (:NOMBDEP (:properties feature))
                          cases (get peru-cases region 0)]
                      (assoc feature
                             :Region region
                             :Cases (get peru-cases region cases))))
                  features))))

(comment
  (oz/start-server! 8082)

  )

(def map-dimensions
  {:width 550 :height 700})

;; TODO scale by population
(oz/view!
 (merge-with merge oz-config map-dimensions
             {:title {:text "COVID-19 cases in South America by country"}
              :data {:name "south-america"
                     :values south-america-geojson-with-data
                     :format {:property "features"}}
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :encoding {:color {:field "Cases" :type "quantitative"}
                         :tooltip [{:field "Country" :type "nominal"}
                                   {:field "Cases" :type "quantitative"}]}
              :selection {:highlight {:on "mouseover" :type "single"}}}))

;; TODO scale by population
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
