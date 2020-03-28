(ns appliedsciencestudio.covid19-clj-viz.explore
  "REPL notebook for exploration through data visualization.

  Intended to be executed one form at a time, interactively in your
  editor-connected REPL"
  (:require [appliedsciencestudio.covid19-clj-viz.common :refer [applied-science-font
                                                                 applied-science-palette
                                                                 oz-config]]
            [appliedsciencestudio.covid19-clj-viz.sources.johns-hopkins :as jh]
            [appliedsciencestudio.covid19-clj-viz.sources.world-bank :as world-bank]
            [clojure.string :as string]
            [clojure.set]
            [jsonista.core :as json]
            [oz.core :as oz]
            [clojure.set :as set :refer [rename-keys]]
            [appliedsciencestudio.covid19-clj-viz.india :as india]))

(comment
  (oz/start-server! 8082))




;;;; ===========================================================================
;;;; A bar chart to compare particular countries

;; Sorted and with some rearranging around `province/country`.
(oz/view!
 (merge-with merge oz-config
             {:title {:text "COVID19 cases in selected countries"}
              :width 800, :height 400
              :data {:values (->> jh/confirmed
                                  (map #(select-keys % [:province-state :country-region jh/last-reported-date]))
                                  ;; restrict to countries we're interested in:
                                  (filter (comp #{"China" "Mainland China"
                                                  "Iran"
                                                  "Italy" "Spain"
                                                  "Germany"} ;; FIXME change to suit your questions
                                                :country-region))
                                  (reduce (fn [acc {:keys [province-state country-region] :as m}]
                                            (conj acc {:location (if (string/blank? province-state)
                                                                   country-region
                                                                   (str province-state ", " country-region))
                                                       :cases (get m jh/last-reported-date)}))
                                          [])
                                  (remove (comp #{"Hubei, Mainland China"} :location))
                                  (sort-by :cases))},
              :mark {:type "bar" :color "#9085DA"}
              :encoding {:x {:field "cases", :type "quantitative"}
                         :y {:field "location", :type "ordinal"
                             :sort nil}}}))


;;;; ===========================================================================
;;;; Cases over time

(defn compare-cases-in [c]
  (->> c
       (jh/new-daily-cases-in :recovered)
       (map (fn [[date recovered]]
              {:date date
               :cases recovered
               :type "recovered"
               :country c}))
       (concat (map (fn [[date cases]]
                      {:date date
                       :cases cases
                       :type "cases"
                       :country c})
                    (jh/new-daily-cases-in :confirmed c))
               (map (fn [[date deaths]]
                      {:date date
                       :cases deaths
                       :type "deaths"
                       :country c})
                    (jh/new-daily-cases-in :deaths c)))
       ;; only 17 February - 11 March
       (filter (comp (fn [d] (or (and (= 2 (Integer/parseInt (subs d 5 7)))
                                     (<= 17 (Integer/parseInt (subs d 8 10))))
                                (and (= 3 (Integer/parseInt (subs d 5 7)))
                                     (> 12 (Integer/parseInt (subs d 8 10))))))
                     :date))))


;;;; Grouped bar chart comparing daily new cases, by kind, in Italy & South Korea
;; See https://twitter.com/daveliepmann/status/1237740992905838593
;; XXX please note the date range in `compare-cases-in`
;; mimicking https://twitter.com/webdevMason/status/1237610911193387008/photo/1
(oz/view!
 (merge-with merge oz-config
             {:title {:text "COVID-19, Italy & South Korea: daily new cases"
                      :font (:mono applied-science-font)
                      :fontSize 30
                      :anchor "middle"}
              :width {:step 16}
              :height 325
              :config {:view {:stroke "transparent"}}
              :data {:values (concat (compare-cases-in "Korea, South") ;; NB: prior to ~March 11, this was "South Korea". Then "Republic of Korea" until ~March 16
                                     (compare-cases-in "Italy"))},
              :mark "bar"
              :encoding {:column {:field "date" :type "temporal" :spacing 10 :timeUnit "monthday"},
                         :x {:field "type" :type "nominal" :spacing 10
                             :axis {:title nil
                                    :labels false}}
                         :y {:field "cases", :type "quantitative"
                             ;; :scale {:domain [0 2000]}
                             :axis {:title nil :grid false}},
                         :color {:field "type", :type "nominal"
                                 :scale {:range ["#f3cd6a" "#de6a83" "#70bebf"]}
                                 :legend {:orient "top"
                                          :title nil
                                          ;; this is clearly not 800px as
                                          ;; the docs claim, but it's the
                                          ;; size I want:
                                          :symbolSize 800
                                          :labelFontSize 24}}
                         :row {:field "country" :type "nominal"}}}))

(comment
  ;; last 10 days of new cases in Deutschland
  (vals (take 10 (sort-by key #(compare %2 %1) (jh/new-daily-cases-in :confirmed "Germany"))))
  ;; (1477 1210 910 1597 170 451 281 136 241 129)

  (vals (take 10 (sort-by key #(compare %2 %1) (jh/new-daily-cases-in :confirmed "Italy")))))
  ;; (3233 3590 3497 5198 0 2313 977 1797 1492 1247)
  



;;;; ===========================================================================
;;;; Daily new cases in a particular country over the past N days
(oz/view!
 (merge-with merge oz-config
             {:title {:text "Daily new confirmed COVID-19 cases"
                      :font (:mono applied-science-font)
                      :fontSize 30
                      :anchor "middle"}
              :width 500 :height 325
              :data {:values (let [country "Germany"] ;; FIXME change country here
                               (->> (jh/new-daily-cases-in :confirmed country)
                                    (sort-by key #(compare %2 %1))
                                    (take 20)
                                    vals
                                    (into [])
                                    (map-indexed (fn [i n] {:cases n
                                                            :country country
                                                            :days-ago i}))))},
              :mark {:type "bar" :size 24}
              :encoding {:x {:field "days-ago" :type "ordinal"
                             :sort "descending"}
                         :y {:field "cases", :type "quantitative"}
                         :tooltip {:field "cases" :type "quantitative"}
                         :color {:field "country" :type "nominal"
                                 :scale {:range (mapv val applied-science-palette)}}}}))

;;;; ===========================================================================
;;;; Choropleth: European countries' COVID19 rate of infection
;; geojson from https://github.com/leakyMirror/map-of-europe/blob/master/GeoJSON/europe.geojson

(def europe-dimensions
  {:width 750 :height 750})

(def europe-geojson
  (json/read-value (java.io.File. "resources/public/public/data/europe.geo.json")
                   (json/object-mapper {:decode-key-fn true})))

(comment
  ;; Which countries are in this geoJSON?
  (map :NAME (map :properties (:features europe-geojson)))
  ;; ("Azerbaijan" "Albania" "Armenia" "Bosnia and Herzegovina" "Bulgaria" "Cyprus" "Denmark" "Ireland" "Estonia" "Austria" "Czech Republic" "Finland" "France" "Georgia" "Germany" "Greece" "Croatia" "Hungary" "Iceland" "Israel" "Italy" "Latvia" "Belarus" "Lithuania" "Slovakia" "Liechtenstein" "The former Yugoslav Republic of Macedonia" "Malta" "Belgium" "Faroe Islands" "Andorra" "Luxembourg" "Monaco" "Montenegro" "Netherlands" "Norway" "Poland" "Portugal" "Romania" "Republic of Moldova" "Slovenia" "Spain" "Sweden" "Switzerland" "Turkey" "United Kingdom" "Ukraine" "San Marino" "Serbia" "Holy See (Vatican City)" "Russia")

  (clojure.set/difference (set (map (comp #(get world-bank/normalize-country % %) :NAME)
                                    (map :properties (:features europe-geojson))))
                          jh/countries)

  ;; What outliers are making the map less useful?
  (sort-by second (map (juxt (comp :NAME :properties) :rate)
                       (:features
                        (update europe-geojson
                                :features
                                (fn [features]
                                  (mapv (fn [feature]
                                          (let [cntry (:NAME (:properties feature))]
                                            (assoc feature
                                                   :country cntry
                                                   :rate (jh/rate-as-of :confirmed cntry 1))))
                                        features))))))

  (jh/new-daily-cases-in :deaths "Andorra"))



(def europe-infection-datapoints
  (update europe-geojson :features
          (fn [features]
            (mapv (fn [feature]
                    (let [cntry (:NAME (:properties feature))]
                      (assoc feature
                             :country cntry
                             ;; Because some countries (e.g. Germany) are
                             ;; not testing people post-mortem, which
                             ;; drastically reduces their reported
                             ;; deaths, I chose to calculate only
                             ;; confirmed cases.
                             :confirmed-rate (jh/rate-as-of :confirmed cntry 1))))
                  features))))

(comment
  ;; Let's look at the rate of change data
  (sort-by second (map (juxt :country :confirmed-rate) (:features europe-infection-datapoints))))



;; The rate of infection is relatively constant across countries.
(oz/view!
 (merge-with merge oz-config europe-dimensions
             {:title {:text "COVID19 in Europe: Rate of Infection Increase"}
              :data {:values europe-infection-datapoints
                     :format {:type "json" :property "features"}}
              :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
              :encoding {:color {:field "confirmed-rate" :type "quantitative"
                                 :scale {:domain [0 (->> (:features europe-infection-datapoints)
                                                         ;; Nix the outlier TODO automate this
                                                         (remove (comp #{"Andorra"} :country))
                                                         (map :confirmed-rate)
                                                         (apply max))]
                                         :range [(:blue applied-science-palette)
                                                 (:green applied-science-palette)]}}
                         :tooltip [{:field "country" :type "nominal"}
                                   {:field "confirmed-rate" :type "quantitative"}]}
              :selection {:highlight {:on "mouseover" :type "single"}}}))


;;;; ===========================================================================
;;;; Total Cases of Coronavirus Outside of China
;; from Chart 9 https://medium.com/@tomaspueyo/coronavirus-act-today-or-people-will-die-f4d3d9cd99ca

(oz/view!
 (merge-with merge oz-config
             {:title {:text "Total Cases of Coronavirus Outside of China"
                      :subtitle "(Countries with >50 cases as of 11.3.2020)"}
              :width 1200 :height 700
              :data {:values
                     (->> (map (fn [ctry] [ctry (zipmap jh/csv-dates (jh/country-totals ctry jh/confirmed))])
                               (clojure.set/difference jh/countries #{"Mainland China" "China" "Others"}))
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
                         :color {:field "country", :type "nominal"}
                         :tooltip {:field "country", :type "nominal"}}}))

;;;; ===========================================================================

;; Minimum viable geographic visualization of India
(oz/view! {:data {:url "/public/data/india-all-states.geo.json"
                  :format {:type "json"
                           :property "features"}}
           :mark "geoshape"})

(def india-dimensions
  {:width 750 :height 750})

(def india-geojson-with-data
  (update (json/read-value (java.io.File. "resources/public/public/data/india-all-states.geo.json")
                           (json/object-mapper {:decode-key-fn true}))
          :features
          (fn [features]
            (mapv (fn [feature]
                    (let [cases (get-in india/india-data [(:NAME_1 (:properties feature)) :confirmed] 0)
                          cases (if (not(= 0 cases))
                                  (Long/parseLong cases)
                                  cases)
                          pop   (get india/state-population (:NAME_1 (:properties feature)))
                          pop (if (not(= 0 pop))
                                (Long/parseLong (string/replace pop "," ""))
                                pop)
                          cases-per-100k (if (< 0 cases)
                                           (double (/ cases (/ pop 100000)))
                                           0)]
                      (assoc feature
                        :State          (:NAME_1 (:properties feature))
                        :Cases          cases
                        :Deaths         (get-in india/india-data [(:NAME_1 (:properties feature)) :deaths] 0)
                        :Recovered      (get-in india/india-data [(:NAME_1 (:properties feature)) :recovered] 0)
                        :Cases-per-100k cases-per-100k)))

                  features))))

(comment
  (json/write-value (java.io.File. "resources/public/public/data/india-all-states-created.geo.json")
                    india-geojson-with-data))

(oz/view!
  (merge-with merge oz-config india-dimensions
              {:title {:text "Current India Covid Scenario"}
               :data {:name "india"
                      :values india-geojson-with-data
                      :format {:property "features"}},
               :mark {:type "geoshape" :stroke "white" :strokeWidth 1}
               :encoding {:color {:field "Cases-per-100k",
                                  :type "quantitative"
                                  :scale {:field "cases-per-100k",
                                          :scale {:type "log"}
                                          :type "quantitative"}}
                          :tooltip [{:field "State" :type "nominal"}
                                    {:field "Cases" :type "quantitative"}
                                    {:field "Deaths" :type "quantitative"}
                                    {:field "Recovered" :type "quantitative"}]}
               :selection {:highlight {:on "mouseover" :type "single"}}}))

;;;; ===========================================================================
;;;; Bar chart with Indian states

(oz/view!(merge oz-config
                {:title "Confirmed COVID19 cases in India",
                 :data {:values (->> india/india-data
                                     vals
                                     (map #(select-keys % [:state :confirmed]))
                                     (reduce (fn [acc m]
                                               (conj acc {:state (:state m)
                                                          :confirmed (:confirmed m)}))
                                             [])
                                     (sort-by :state)
                                     ;; ;; FIXME this is the line to toggle:
                                     (remove (comp #{"Total"} :state)))},
                 :mark {:type "bar" :color "#9085DA"}
                 :encoding {:x {:field "confirmed", :type "quantitative"}
                            :y {:field "state", :type "ordinal"
                                :sort nil}}}))

;;;; Bar chart with Indian states and Chinese provinces

(oz/view!
  (merge oz-config
         {:title "Confirmed COVID19 cases in China and India",
          :data {:values (let [date "2020-03-19"]
                           (->> jh/confirmed
                                ;; Notice improved readability from working with seq of maps:
                                (map #(select-keys % [:province-state :country-region date]))
                                (filter (comp #{"China" "Mainland China"} :country-region))
                                (reduce (fn [acc m]
                                          (conj acc {:state-province (:province-state m)
                                                     :confirmed (get m date)}))
                                        [])
                                (concat (->> india/india-data
                                             vals
                                             (map (comp #(select-keys % [:state-province :confirmed])
                                                        #(rename-keys % {:state :state-province})))
                                             (sort-by :state)))
                                (remove (comp #{"Hubei"} :state-province))
                                (remove (comp #{"Total"} :state-province))))},
          :mark {:type "bar" :color "#9085DA"}
          :encoding {:x {:field "confirmed", :type "quantitative"}
                     :y {:field "state-province", :type "ordinal"
                         :sort nil}}}))
