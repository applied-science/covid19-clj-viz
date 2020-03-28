(ns appliedsciencestudio.covid19-clj-viz.article
  "Vega-lite visualizations for 'COVID19 in the REPL' article [1].
  Intended to be executed one form at a time, interactively in your
  editor-connected REPL. Some additional visualizations are included
  for pedagogical clarity.

  Some of this code is repeated in other namespaces, because this
  namespace is intended to stand alone.

  [1] http://www.appliedscience.studio/articles/covid19.html"
  (:require [appliedsciencestudio.covid19-clj-viz.china :as china]
            [appliedsciencestudio.covid19-clj-viz.deutschland :as deutschland]
            [appliedsciencestudio.covid19-clj-viz.johns-hopkins :as jh]
            [clojure.set :as set :refer [rename-keys]]
            [clojure.string :as string]
            [jsonista.core :as json]
            [oz.core :as oz]))

;; Our visualizations are powered by Vega(-lite), which we connect to
;; through an Oz server. Oz will open a browser window to display the
;; visualizations.
(oz/start-server! 8082)


;;;; ===========================================================================
;;;; Minimum viable geographic visualization
(oz/view! {:data {:url "/public/data/deutschland-bundeslaender.geo.json"
                  ;; We depend on Vega-lite's auto-parsing of GeoJSON:
                  :format {:type "json" :property "features"}}
           :mark "geoshape"})


;;;; ===========================================================================
;; Setup for more interesting visualizations
(def applied-science-palette
  {:pink   "#D46BC8"
   :green  "#38D996"
   :blue   "#4FADFF"
   :purple "#9085DA"})

(def applied-science-font
  {:mono "IBM Plex Mono"
   :sans "IBM Plex Sans"})

(def oz-config
  "Default settings for Oz visualizations"
  {:config {:style {:cell {:stroke "transparent"}}
            :legend {:labelFont (:mono applied-science-font)
                     :labelFontSize 12
                     :titleFont (:mono applied-science-font)
                     :gradientThickness 40}
            :axis {:labelFont (:mono applied-science-font)
                   :titleFont (:mono applied-science-font)
                   :titleFontSize 20}}
   :title {:font (:sans applied-science-font)
           :fontSize 14
           :anchor "middle"}})

(def germany-dimensions
  {:width 550 :height 700})

;; I found that the easiest way to merge two separate data sources in
;; Vega is to do it ourselves, before the data gets to Vega. Clojure
;; makes this easy, so I'd rather do it here than fiddle with
;; poorly-documented Vega-lite config.
(def deutschland-geojson-with-data
  "GeoJSON of Germany, plus data points we gather from other sources."
  (update (json/read-value (java.io.File. "resources/public/public/data/deutschland-bundeslaender-original.geo.json")
                           (json/object-mapper {:decode-key-fn true}))
          :features
          (fn [features]
            (mapv (fn [feature]
                    (assoc feature
                           :Bundesland     (:NAME_1 (:properties feature))
                           :Cases          (get-in deutschland/bundeslaender-data [(:NAME_1 (:properties feature)) :cases] 0)
                           :Cases-per-100k (get-in deutschland/bundeslaender-data [(:NAME_1 (:properties feature)) :cases-per-100k] 0)))
                  features))))


(comment

  ;;;; Create new GeoJSONs with COVID19 data added

  ;; Germany
  ;; medium quality GeoJSON from https://github.com/isellsoap/deutschlandGeoJSON/blob/master/2_bundeslaender/3_mittel.geojson
  (json/write-value (java.io.File. "resources/public/public/data/deutschland-bundeslaender.geo.json")
                    deutschland-geojson-with-data)

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


;;;; ===========================================================================
;;;; Geographic visualization of cases in each Germany state, shaded proportional to population
(oz/view!
 (merge-with merge oz-config germany-dimensions
             {:title {:text "COVID19 cases in Germany, by state, per 100k inhabitants"}
              :data {:name "germany"
                     ;; FIXME this keeps getting cached somewhere in Firefox or Oz
                     ;; :url "/public/data/deutschland-bundeslaender.geo.json",
                     :values deutschland-geojson-with-data
                     :format {:property "features"}},
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :encoding {:color {:field "Cases-per-100k",
                                 :type "quantitative"
                                 :scale {:domain [0
                                                  ;; NB: compare Hubei's 111 to the German maximum. It was 0.5 when I started this project. Evaluate the next expression to see its current value.
                                                  (apply max (map :cases-per-100k (vals deutschland/bundeslaender-data)))]}}
                         :tooltip [{:field "Bundesland" :type "nominal"}
                                   {:field "Cases" :type "quantitative"}]}
              :selection {:highlight {:on "mouseover" :type "single"}}}))


;;;; ===========================================================================
;;;; Deceptive version of that same map
;;    - red has emotional valence ["#fde5d9" "#a41e23"]
;;    - we report cases without taking population into account
(oz/view! (merge oz-config germany-dimensions
                 {:title "COVID19 cases in Germany (*not* population-scaled)"
                  :data {:name "germany"
                         ;; FIXME this keeps getting cached somewhere in Firefox or Oz
                         ;; :url "/public/data/deutschland-bundeslaender.geo.json",
                         :values deutschland-geojson-with-data
                         :format {:property "features"}},
                  :mark {:type "geoshape"  :stroke "white" :strokeWidth 1}
                  :encoding {:color {:field "Cases",
                                     :type "quantitative"
                                     :scale { ;; from https://www.esri.com/arcgis-blog/products/product/mapping/mapping-coronavirus-responsibly/
                                             :range ["#fde5d9" "#a41e23"]}}
                             :tooltip [{:field "Bundesland" :type "nominal"}
                                       {:field "Cases" :type "quantitative"}]}
                  :selection {:highlight {:on "mouseover" :type "single"}}}))


;;;; ===========================================================================
;;;; Bar chart with German states, all of Germany, and Chinese provinces

(def barchart-dimensions
  {:width 510 :height 800})

;; Bar chart of the severity of the outbreak across regions in China and Germany
;; (with or without the outlier that is China's Hubei province)
(oz/view!
 (merge oz-config
        {:title "Confirmed COVID19 cases in China and Germany",
         :data {:values (let [date "2020-03-19"]
                          (->> jh/covid19-confirmed-csv
                               ;; Notice improved readability from working with seq of maps:
                               (map #(select-keys % [:province-state :country-region date]))
                               (filter (comp #{"China" "Mainland China" "Germany"} :country-region))
                               (reduce (fn [acc m]
                                         (conj acc {:state-province (if (string/blank? (:province-state m))
                                                                      "(All German federal states)"
                                                                      (:province-state m))
                                                    :cases (get m date)}))
                                       [])
                               (concat (->> deutschland/bundeslaender-data
                                            vals
                                            (remove (comp #{"Gesamt"} :bundesland))
                                            (map (comp #(select-keys % [:state-province :cases])
                                                       #(rename-keys % {:bundesland :state-province})))
                                            (sort-by :state-province)))
                               ;; ;; FIXME this is the line to toggle:
                               (remove (comp #{"Hubei"} :state-province))
                               (sort-by :cases)))},
         :mark {:type "bar" :color "#9085DA"}
         :encoding {:x {:field "cases", :type "quantitative"}
                    :y {:field "state-province", :type "ordinal"
                        :sort nil}}}))


;;;; ===========================================================================
;;;; Useless China chloropleth

(def china-dimensions
  {:width 570 :height 450})

(oz/view! (merge oz-config china-dimensions
                 {:data {:name "map"
                         :url "/public/data/china-provinces.geo.json"
                         :format {:property "features"}},
                  :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
                  :encoding {:color {:field "cases-per-100k",
                                     :type "quantitative"}
                             :tooltip [{:field "province" :type "nominal"}
                                       {:field "cases" :type "quantitative"}]}}))


;;;; ===========================================================================
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


;;;; ===========================================================================
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


;;;; ===========================================================================
;;;; Total Cases of Coronavirus Outside of China
;; from Chart 9 https://medium.com/@tomaspueyo/coronavirus-act-today-or-people-will-die-f4d3d9cd99ca

(oz/view!
 (merge-with merge oz-config
             {:title {:text "Total Cases of Coronavirus Outside of China"
                      :subtitle "(Countries with >50 cases as of 11.3.2020)"}
              :width 1200 :height 700
              :data {:values
                     (->> (map (fn [ctry] [ctry (zipmap jh/csv-dates (jh/country-totals ctry jh/covid19-confirmed-csv))])
                               (set/difference jh/countries #{"Mainland China" "China" "Others"} ))
                          (reduce (fn [vega-values [country date->cases]]
                                    (if (> 500 (apply max (vals date->cases))) ; ignore countries with fewer than X cases
                                      vega-values
                                      (apply conj vega-values
                                             (map (fn [[d c]]
                                                    {:date d
                                                     :cases c
                                                     :country country})
                                                  date->cases))))
                                  [])
                          ;; purely for our human reading convenience:
                          (sort-by (juxt :country :date)))}
              :mark {:type "line" :strokeWidth 4 :point "transparent"}
              :encoding {:x {:field "date", :type "temporal"},
                         :y {:field "cases", :type "quantitative"}
                         ;; TODO tooltip for country
                         :color {:field "country", :type "nominal"}
                         :tooltip {:field "country", :type "nominal"}}}))
