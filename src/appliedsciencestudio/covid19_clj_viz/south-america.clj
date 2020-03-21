(ns appliedsciencestudio.covid19-clj-viz.south-america
  (:require [clojure.data.csv :as csv]))

(def cases
  "Current number of COVID19 cases in South America, by countries"
  (->> (csv/read-csv (slurp "resources/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_19-covid-Confirmed.csv"))
       (map (juxt first second last))
       (filter (comp #{"Peru" "Brazil" "Argentina"
                       "Chile" "Bolivia" "Colombia"
                       "Ecuador" "Uruguay" "Paraguay" "Venezuela"
                       "Suriname" "Guyana"} second))
       ))

(def peru-cases
  "Current number of COVID19 cases in Peru by regions"
  (->> (csv/read-csv (slurp "resources/peru-covit19cases.csv"))))