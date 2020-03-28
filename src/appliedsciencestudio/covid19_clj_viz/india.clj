(ns appliedsciencestudio.covid19-clj-viz.india
  (:require [jsonista.core :as json]
            [meta-csv.core :as mcsv]))

(defonce india-covid-data (slurp "https://api.covid19india.org/data.json"))

(def state-population
  "From https://en.wikipedia.org/wiki/List_of_states_and_union_territories_of_India_by_population"
  (let [state-population-list (mcsv/read-csv "resources/india.state-population.tsv")
        state-population-map  (into {} (mapv (fn [each-state]
                                               (-> (first each-state)
                                                   (hash-map (second each-state)))) state-population-list))]
    state-population-map))

(def india-data
  (->> (json/read-value india-covid-data (json/object-mapper {:decode-key-fn true}))
       :statewise
       (mapv (fn [each-state-data] (-> (:state each-state-data)
                                       (hash-map each-state-data))))
       (into {})))
