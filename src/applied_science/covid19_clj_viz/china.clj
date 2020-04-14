(ns applied-science.covid19-clj-viz.china
  (:require [clojure.string :as string]
            [applied-science.covid19-clj-viz.sources.johns-hopkins :as jh]
            [meta-csv.core :as mcsv]))

(def cases
  "Current number of confirmed COVID19 cases in China, by province"
  (->> jh/confirmed
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

(def province-data
  (reduce-kv (fn [datapoints province n]
               (conj datapoints
                     (let [pop (get province-populations province)]
                       {:province province
                        :cases n
                        :population pop
                        :cases-per-100k (double (/ n (/ pop 100000)))
                        :cases-binned (cond
                                        (> n 1000) 1000
                                        (> n 500)  500
                                        (> n 200)  200
                                        (> n 100)  100
                                        :else      0)})))
             []
             cases))
