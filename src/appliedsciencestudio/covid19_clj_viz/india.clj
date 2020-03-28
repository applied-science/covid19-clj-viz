(ns appliedsciencestudio.covid19-clj-viz.india
  (:require [jsonista.core :as json]
            [meta-csv.core :as mcsv]
            [oz.core :as oz]))

(defonce india-covid-data
  (slurp "https://api.covid19india.org/data.json"))

(def state-population
  "From https://en.wikipedia.org/wiki/List_of_states_and_union_territories_of_India_by_population"
  (into {} (mcsv/read-csv "resources/india.state-population.tsv")))

(def india-data
  (->> (json/read-value india-covid-data (json/object-mapper {:decode-key-fn true}))
       :statewise
       (mapv (fn [each-state-data] (-> (:state each-state-data)
                                      (hash-map each-state-data))))
       (into {})))


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
              {:title {:text "Current India COVID-19 Scenario"}
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
