(ns applied-science.covid19-clj-viz.covid19-in-the-repl
  "Vega-lite visualizations for 'COVID19 in the REPL' article [1].

  This is a REPL notebook, meaning it is intended to be executed one
  form at a time, interactively in your editor-connected REPL. Some
  additional visualizations are included for illustration.

  Some of this code is repeated in other namespaces, because this
  namespace is intended to stand somewhat alone.

  [1] http://www.appliedscience.studio/articles/covid19.html"
  (:require [applied-science.covid19-clj-viz.china :as china]
            [applied-science.covid19-clj-viz.deutschland :as deutschland]
            [applied-science.covid19-clj-viz.sources.johns-hopkins :as jh]
            [clojure.set :refer [rename-keys]]
            [applied-science.waqi :as waqi]))

;; Our visualizations are powered by Vega(-lite), which we connect to
;; through Waqi, which will open a browser window to display the
;; visualizations.
(comment
  ;; It is only necessary to evaluate the following line if you want
  ;; to run the Waqi webserver on a port other than 8080. Otherwise,
  ;; the first call to `plot!` will start the server automatically.
  #_ (waqi/start-server! 8082)

  )


;;;; ===========================================================================
;;;; Minimum viable geographic visualization
(waqi/plot! {:data {:url "/public/data/deutschland-bundeslaender.geo.json"
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

(def vega-lite-config
  "Default settings for Vega-Lite visualizations"
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


;;;; ===========================================================================
;;;; Geographic visualization of cases in each Germany state, shaded proportional to population
(waqi/plot!
 (merge-with merge vega-lite-config germany-dimensions
             {:title {:text "COVID19 cases in Germany, by state, per 100k inhabitants"}
              :data {:name "germany"
                     :url "/public/data/deutschland-bundeslaender.geo.json",
                     :format {:property "features"}}
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :transform [{:lookup "properties.NAME_1"
                           :from {:data {:name "bundeslaender"
                                         :values (vals deutschland/bundeslaender-data)}
                                  :key "bundesland"
                                  :fields ["bundesland"
                                           "cases"
                                           "cases-per-100k"
                                           "difference-carried-forward"
                                           "deaths"
                                           "particularly-affected-areas"]}}]
              :encoding {:color {:field "cases-per-100k",
                                 :type "quantitative"
                                 :scale {:domain [0
                                                  ;; NB: compare Hubei's 111 to the German maximum. It was 0.5 when I started this project. Evaluate the next expression to see its current value.
                                                  (apply max (map :cases-per-100k (vals deutschland/bundeslaender-data)))]}}
                         :tooltip [{:field "bundesland" :type "nominal"}
                                   {:field "cases" :type "quantitative"}]}
              :selection {:highlight {:on "mouseover" :type "single"}}}))


;;;; ===========================================================================
;;;; Deceptive version of that same map
;;    - red has emotional valence ["#fde5d9" "#a41e23"]
;;    - we report cases without taking population into account
(waqi/plot!
 (merge-with merge vega-lite-config germany-dimensions
             {:title {:text "COVID19 cases in Germany (*not* population-scaled)"}
              :data {:name "germany"
                     :url "/public/data/deutschland-bundeslaender.geo.json",
                     :format {:property "features"}},
              :mark {:type "geoshape"  :stroke "white" :strokeWidth 1}
              :transform [{:lookup "properties.NAME_1"
                           :from {:data {:name "bundeslaender"
                                         :values (vals deutschland/bundeslaender-data)}
                                  :key "bundesland"
                                  :fields ["bundesland"
                                           "cases"
                                           "cases-per-100k"
                                           "difference-carried-forward"
                                           "deaths"
                                           "particularly-affected-areas"]}}]              
              :encoding {:color {:field "cases",
                                 :type "quantitative"
                                 :scale { ;; from https://www.esri.com/arcgis-blog/products/product/mapping/mapping-coronavirus-responsibly/
                                         :range ["#fde5d9" "#a41e23"]}}
                         :tooltip [{:field "bundesland" :type "nominal"}
                                   {:field "cases" :type "quantitative"}]}
              :selection {:highlight {:on "mouseover" :type "single"}}}))


;;;; ===========================================================================
;;;; Bar chart with German states, all of Germany, and Chinese provinces

(def barchart-dimensions
  {:width 510 :height 800})

;; Bar chart of the severity of the outbreak across regions in China and Germany
;; (with or without the outlier that is China's Hubei province)
;; NB: the situation and therefore the data have changed dramatically
;; since the article was published, so this chart is _very_
;; different!
(waqi/plot!
 (merge-with
  merge vega-lite-config
  {:title {:text "Confirmed COVID19 cases in China and Germany (on specific date)"}
   :width 650 :height 750
   ;; Here is the snippet of code the article examines in detail:
   :data {:values (let [date "2020-03-04"]
                    (->> jh/confirmed
                         (filter (comp #{"China"} :country-region))
                         (map #(select-keys % [:province-state
                                               :country-region
                                               date]))
                         (concat (map #(assoc % :country "Germany")
                                      deutschland/legacy-cases)
                                 [{:country "Germany"
                                   :state "(All of Germany)"
                                   :cases (apply + (map :cases deutschland/legacy-cases))}])
                         (map #(rename-keys % {:state :province-state
                                               date :cases
                                               :country-region :country}))
                         ;; ;; FIXME this is the line to toggle:
                         (remove (comp #{"Hubei"} :province-state))))},
   :mark "bar"
   :encoding {:x {:field "cases", :type "quantitative"}
              :y {:field "province-state", :type "ordinal"
                  ;; sort along the y-axis in descending order of the x-value:
                  :sort "-x"}
              :color {:field "country" :type "ordinal"
                      :scale {:range [(:green applied-science-palette)
                                      (:purple applied-science-palette)]}}
              :tooltip [{:field "province-state" :type "nominal"}
                        {:field "country" :type "nominal"}
                        {:field "cases" :type "quantitative"}]}}))


;;;; ===========================================================================
;;;; Useless China chloropleth

(def china-dimensions
  {:width 570 :height 450})

(waqi/plot!
 (merge-with merge vega-lite-config china-dimensions
             {:data {:name "china"
                     :url "/public/data/china-provinces.geo.json"
                     :format {:property "features"}},
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :transform [{:lookup "properties.province"
                           :from {:data {:name "provinces"
                                         :values china/province-data}
                                  :key "province"
                                  :fields ["cases" "cases-per-100k"]}}]
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
(waqi/plot!
 (merge-with merge vega-lite-config china-dimensions
             {:title {:text "COVID19 cases in China"}
              :data {:name "map"
                     :url "/public/data/china-provinces.geo.json",
                     :format {:property "features"}},
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :transform [{:lookup "properties.province"
                           :from {:data {:name "provinces"
                                         :values china/province-data}
                                  :key "province"
                                  :fields ["cases" "cases-per-100k" "cases-binned"]}}]
              :encoding {:color {:field "cases-binned",
                                 :bin true ;; <-- we pre-processed the
                                           ;; data into bins, so here
                                           ;; we merely notify Vega.
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
(waqi/plot!
 (merge-with merge vega-lite-config china-dimensions
             {:title {:text "COVID19 cases in China per 100k inhabitants, log-scaled"}
              :data {:name "map"
                     :url "/public/data/china-provinces.geo.json",
                     :format {:property "features"}},
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :transform [{:lookup "properties.province"
                           :from {:data {:name "provinces"
                                         :values china/province-data}
                                  :key "province"
                                  :fields ["cases" "cases-per-100k"]}}]
              :encoding {:color {:field "cases-per-100k",
                                 :scale {:type "log"}
                                 :type "quantitative"}
                         :tooltip [{:field "province" :type "nominal"}
                                   {:field "cases" :type "quantitative"}
                                   {:field "cases-per-100k" :type "quantitative"}]}}))
