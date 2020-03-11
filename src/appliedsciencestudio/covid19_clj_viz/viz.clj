(ns appliedsciencestudio.covid19-clj-viz.viz
  (:require [appliedsciencestudio.covid19-clj-viz.china :as china]
            [appliedsciencestudio.covid19-clj-viz.deutschland :as deutschland]
            [clojure.data.csv :as csv]
            [clojure.string :as string]
            [jsonista.core :as json]
            [oz.core :as oz])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter]))

(oz/start-server! 8082)

(comment

  ;;;; Create new GeoJSONs with population & COVID19 data added

  ;; Germany
  ;; medium quality GeoJSON from https://github.com/isellsoap/deutschlandGeoJSON/blob/master/2_bundeslaender/3_mittel.geojson
  (->> (update (json/read-value (java.io.File. "resources/public/public/data/deutschland-bundeslaender-original.geo.json")
                                (json/object-mapper {:decode-key-fn true}))
               :features
               (fn [features]
                 (mapv (fn [feature]
                         (-> feature
                             (assoc :Bundesland     (:NAME_1 (:properties feature)))
                             (assoc :Cases          (get deutschland/cases (:NAME_1 (:properties feature)) 0))
                             (assoc :Population     (get deutschland/population (get deutschland/normalize-bundesland (:NAME_1 (:properties feature)) (:NAME_1 (:properties feature)))))
                             (assoc :Cases-per-100k (get-in deutschland/bundeslaender-data [(:NAME_1 (:properties feature)) :cases-per-100k] 0))))
                       features)))
       (json/write-value (java.io.File. "resources/public/public/data/deutschland-bundeslaender.geo.json")))

  ;; China
  (->> (update (json/read-value (java.io.File. "resources/public/public/data/china-provinces-original.geo.json")
                                (json/object-mapper {:decode-key-fn true}))
               :features
               (fn [features]
                 (map (fn [feature]
                        (let [cases (second (first (filter (comp #{(:province (:properties feature))} first)
                                                           china/cases)))
                              pop (get china/province-populations (:province (:properties feature)))
                              cases-per-100k (double (/ cases (/ pop 100000)))]
                          (assoc feature
                                 :province (-> feature :properties :province)
                                 :cases cases
                                 :population pop
                                 :cases-binned (cond
                                                 (> cases 1000) 1000
                                                 (> cases 500)  500
                                                 (> cases 200)  200
                                                 (> cases 100)  100
                                                 :else          0)
                                 :cases-per-100k cases-per-100k)))
                      features)))
       (json/write-value (java.io.File. "resources/public/public/data/china-provinces.geo.json")))

  )

;; Minimum viable geographic visualization
(oz/view! {:data {:url "/public/data/deutschland-bundeslaender.geo.json"
                  :format {:type "json"
                           :property "features"}}
           :mark "geoshape"})

(def oz-config
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

(def germany-dimensions
  {:width 550 :height 700})

;; Geographic visualization of cases in each Germany state, shaded proportional to population
(oz/view!
 (merge-with merge oz-config germany-dimensions
             {:title {:text "COVID19 cases in Germany, by state, per 100k inhabitants"}
              :data {:name "germany"
                     :url "/public/data/deutschland-bundeslaender.geo.json",
                     :format {:property "features"}},
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :encoding {:color {:field "Cases-per-100k",
                                 :type "quantitative"
                                 :scale {:domain [0
                                                  ;; NB: compare Hubei's 111 to German maximum. (It was 0.5 when I started this project, and ~1 now.)
                                                  (apply max (map :cases-per-100k (vals deutschland/bundeslaender-data)))]}}
                         :tooltip [{:field "Bundesland" :type "nominal"}
                                   {:field "Cases" :type "quantitative"}]}
              :selection {:highlight {:on "mouseover" :type "single"}}}))

;; Deceptive version of that same map
;;   - red has emotional valence ["#fde5d9" "#a41e23"]
;;   - we report cases without taking population into account
(oz/view! (merge oz-config germany-dimensions
                 {:title "COVID19 cases in Germany (*not* population-scaled)"
                  :data {:name "germany"
                         :url "/public/data/deutschland-bundeslaender.geo.json",
                         :format {:property "features"}},
                  :mark {:type "geoshape"  :stroke "white" :strokeWidth 1}
                  :encoding {:color {:field "Cases",
                                     :type "quantitative"
                                     :scale { ;; from https://www.esri.com/arcgis-blog/products/product/mapping/mapping-coronavirus-responsibly/
                                             :range ["#fde5d9" "#a41e23"]}}
                             :tooltip [{:field "Bundesland" :type "nominal"}
                                       {:field "Cases" :type "quantitative"}]}
                  :selection {:highlight {:on "mouseover" :type "single"}}}))

;;;; Bar chart with German states, all of Germany, and Chinese provinces

(def covid19-cases-csv
  "From https://github.com/CSSEGISandData/COVID-19/tree/master/who_covid_19_situation_reports"
  (csv/read-csv (slurp "resources/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_19-covid-Confirmed.csv")))

(def covid19-recovered-csv
  "From https://github.com/CSSEGISandData/COVID-19/tree/master/who_covid_19_situation_reports"
  (csv/read-csv (slurp "resources/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_19-covid-Recovered.csv")))

(def covid19-deaths-csv
  "From https://github.com/CSSEGISandData/COVID-19/tree/master/who_covid_19_situation_reports"
  (csv/read-csv (slurp "resources/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_19-covid-Deaths.csv")))

(defn parse-covid19-date [mm-dd-yy]
  (LocalDate/parse mm-dd-yy (DateTimeFormatter/ofPattern "M/d/yy")))

(def barchart-dimensions
  {:width 510 :height 800})


;; Bar chart of the severity of the outbreak across regions in China and Germany
;; (with or without the outlier that is China's Hubei province)
(oz/view! (merge oz-config barchart-dimensions
                 {:title "COVID19 cases in China and Germany",
                  :data {:values (->> covid19-cases-csv
                                      rest
                                      ;; grab only province/state, country, and latest report of total cases:
                                      (map (juxt first second last))
                                      ;; restrict to countries we're interested in:
                                      (filter (comp #{"Mainland China" "Germany"} second))
                                      (reduce (fn [acc [province country current-cases]]
                                                (if (string/blank? province)
                                                  ;; put the summary of Germany first
                                                  (concat [{:state-or-province "(All German federal states)"
                                                            :cases (Integer/parseInt current-cases)}]
                                                          acc)
                                                  ;; otherwise just add the datapoint to the list
                                                  (conj acc {:state-or-province province
                                                             :cases (Integer/parseInt current-cases)})))
                                              [])
                                      (concat (sort-by :state-or-province (vals deutschland/bundeslaender-data)))
                                      ;; FIXME this is the line to toggle:
                                      (remove (comp #{"Hubei"} :state-or-province))
                                      #_ (sort-by :cases))},
                  :mark {:type "bar" :color "#9085DA"}
                  :encoding {:x {:field "cases", :type "quantitative"}
                             :y {:field "state-or-province", :type "ordinal"
                                 :sort nil}}}))


;;;; Evaluate comparative risk in Europe, to answer question from spouse

(comment

  ;; let's take a quick look at country names in the case data
  (->> covid19-cases-csv
       (map second)
       distinct)

  )

(def european-populations
  "From https://data.worldbank.org/indicator/SP.POP.TOTL"
  (->> (csv/read-csv (slurp "resources/API_SP.POP.TOTL_DS2_en_csv_v2_821007.csv"))
       (drop 4) ;; first few rows are cruft
       (map (comp #(map string/trim %)
                  ;; country name is first column;
                  ;; 2018 data is third-to-last column
                  (juxt first (comp last butlast butlast))))
       (map (fn [[country pop-s]]
              [country (if (string/blank? pop-s)
                         0 ;; (Eritrea and "Not classified" have incomplete data)
                         (Long/parseLong (string/replace pop-s "," "")))]))
       (into {})))

;; Bar chart of cases in Europe (scaled to World Bank population estimate)
(oz/view! (merge oz-config barchart-dimensions
                 {:title "COVID19 cases in European countries, per 100k inhabitants",
                  :data {:values (->> covid19-cases-csv
                                      (map (juxt second last))
                                      (filter (comp #{"France" "Spain" "Germany"
                                                      "Sweden" "Italy" "Switzerland"
                                                      "Finland" "Greece" "UK" "Russia"
                                                      "Belgium" "Croatia" "Austria"
                                                      "North Macedonia" "Norway" "Romania"
                                                      "Denmark" "Netherlands" "Lithuania"
                                                      "Ireland" "Czech Republic" "Portugal"
                                                      "Ukraine"}
                                                    first))
                                      (reduce (fn [acc [country current-cases]]
                                                (conj acc {:country (if (= "UK" country)
                                                                      "United Kingdom"
                                                                      country)
                                                           :cases (Integer/parseInt current-cases)
                                                           :pop (get european-populations country)}))
                                              []))},
                  :mark {:type "bar" :color "#9085DA"}
                  :encoding {:x {:field "cases", :type "quantitative"}
                             :y {:field "country", :type "ordinal"
                                 :sort nil}}}))

(def china-dimensions
  {:width 570 :height 450})

;;;; Useless China chloropleth
(oz/view! (merge oz-config china-dimensions
                 {:data {:name "map"
                         :url "/public/data/china-provinces.geo.json"
                         :format {:property "features"}},
                  :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
                  :encoding {:color {:field "cases-per-100k",
                                     :type "quantitative"}
                             :tooltip [{:field "province" :type "nominal"}
                                       {:field "cases" :type "quantitative"}]}}))

;;;; Deceptive China map
;; As above, but:
;;  - binned, which varies the map
;;  - not scaled to population, which assists that variation
;;  - uses red, which has inappropriate emotional valence
;; This is more visually appealing and _feels_ useful, but is actually quite deceptive.
;; (inspired by https://www.esri.com/arcgis-blog/products/product/mapping/mapping-coronavirus-responsibly/ )
(oz/view! (merge oz-config china-dimensions
                 {:title "COVID19 cases in China"
                  :data {:name "map"
                         :url "/public/data/china-provinces.geo.json",
                         :format {:property "features"}},
                  :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
                  :encoding {:color {:field "cases-binned",
                                     :bin true
                                     :type "quantitative"
                                     :scale {:range ["#fde5d9"
                                                     "#f9af91"
                                                     "#f26a4d"
                                                     "#e22b26"
                                                     "#a41e23"]}}
                             :tooltip [{:field "province" :type "nominal"}
                                       {:field "cases" :type "quantitative"}
                                       {:field "cases-per-100k" :type "quantitative"}]}}))

;;;; "Best we can do" China map
;; We return to the original color scheme, drop the binning and the
;; scary red color scheme, and (most importantly) use a log scale.
;; This requires the viewer to understand orders of magnitude, which
;; might be appropriate for data science but inappropriate for
;; journalism.
(oz/view! (merge oz-config china-dimensions
                 {:title "COVID19 cases in China per 100k inhabitants, log-scaled"
                  :data {:name "map"
                         :url "/public/data/china-provinces.geo.json",
                         :format {:property "features"}},
                  :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
                  :encoding {:color {:field "cases-per-100k",
                                     :scale {:type "log"}
                                     :type "quantitative"}
                             :tooltip [{:field "province" :type "nominal"}
                                       {:field "cases" :type "quantitative"}
                                       {:field "cases-per-100k" :type "quantitative"}]}}))

;; Beyond this we rely on charts.
