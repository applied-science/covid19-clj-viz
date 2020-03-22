(ns appliedsciencestudio.covid19-clj-viz.china
  (:require [clojure.string :as string]
            [meta-csv.core :as mcsv]))

(def cases
  ;; TODO refactor to rely on johns hopkins ns
  "Current number of COVID19 cases in China, by province"
  (->> (mcsv/read-csv "resources/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_19-covid-Confirmed.csv"
                      ;; NB: Above file relies on cloning Johns Hopkins repo. See README.
                      {:field-names-fn (comp keyword #(string/replace % #"/" "-") string/lower-case)})
       (filter (comp #{"China"} :country-region))
       (reduce (fn [m row]
                 (assoc m (:province-state row) (second (last row)))) ; most recent date
               {})))

(def province-populations
  "From https://en.m.wikipedia.org/wiki/List_of_Chinese_administrative_divisions_by_population#Current_population
  with some hand-correction"
  (->> (mcsv/read-csv "resources/china.province-population.tsv")
       ;; only take the name of the province and 2017 population data
       (mapv #(let [[province pop-s] %]
                [province (Integer/parseInt (string/replace pop-s "," ""))]))
       (into {})))
