(ns appliedsciencestudio.covid19-clj-viz.explore
  "REPL notebook for exploration through data visualization.

  Intended to be executed one form at a time, interactively in your
  editor-connected REPL"
  (:require [appliedsciencestudio.covid19-clj-viz.china :as china]
            [appliedsciencestudio.covid19-clj-viz.deutschland :as deutschland]
            [appliedsciencestudio.covid19-clj-viz.sources.johns-hopkins :as jh]
            [appliedsciencestudio.covid19-clj-viz.sources.world-bank :as world-bank]
            [clojure.string :as string]
            [jsonista.core :as json]
            [oz.core :as oz])
  (:import [java.time LocalDate]
           [java.time.temporal ChronoUnit]
           [java.time.format DateTimeFormatter]))

(oz/start-server! 8082)

;; Now some setup for more interesting visualizations
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


;;;; ===========================================================================
;;;; A bar chart to compare particular countries

;; Sorted and with some rearranging around `province/country`.
(oz/view!
 (merge oz-config
        {:title "COVID19 cases in selected countries",
         :width 510, :height 200
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
       ;; only since 15 February
       (filter (comp (fn [d] (or (and (= 2 (Integer/parseInt (subs d 5 7)))
                                     (<= 15 (Integer/parseInt (subs d 8 10))))
                                (< 2 (Integer/parseInt (subs d 5 7)))))
                     :date))))


;;;; Grouped bar chart comparing daily new cases, by kind, in Italy & South Korea
;; mimicking https://twitter.com/webdevMason/status/1237610911193387008/photo/1
(oz/view! (merge-with
           merge oz-config
           {:title {:text "COVID-19, Italy & South Korea: daily new cases"
                    :font "IBM Plex Mono"
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

  (vals (take 10 (sort-by key #(compare %2 %1) (jh/new-daily-cases-in :confirmed "Italy"))))
  ;; (3233 3590 3497 5198 0 2313 977 1797 1492 1247)
  
  )

(oz/view!
 (merge-with merge oz-config
             {:title {:text "Daily new confirmed COVID-19 cases in Germany"
                      :font "IBM Plex Mono"
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
