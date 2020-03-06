(ns appliedsciencestudio.covid19-clj-viz.china
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [jsonista.core :as json]))

(def cases
  "Current number of COVID19 cases in China, by province"
  (->> (csv/read-csv (slurp "resources/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_19-covid-Confirmed.csv"))
       rest
       (map (juxt first second last))
       (filter (comp #{"Mainland China"} second))
       (reduce (fn [acc [province _ n]]
                 (assoc acc province (Integer/parseInt n)))
               {})))

(def province-populations
  "from https://en.m.wikipedia.org/wiki/List_of_Chinese_administrative_divisions_by_population
  with some hand-correction"
  (->> (csv/read-csv (slurp "resources/china.province-population.tsv")
                     :separator \tab)
       ;; only take the name of the province and 2017 population data
       (mapv #(let [[province pop-s] (map string/trim %)]
                [province (Integer/parseInt (string/replace pop-s "," ""))]))
       (into {})))

 
