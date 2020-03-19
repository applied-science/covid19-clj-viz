(ns appliedsciencestudio.covid19-clj-viz.world-bank
  (:require [clojure.data.csv :as csv]
            [clojure.string :as string]))

(def normalize-country
  "Mappings to normalize naming & typographic variation to a single standard."
  {"UK" "United Kingdom"
   "The former Yugoslav Republic of Macedonia" "North Macedonia"
   "Republic of Moldova" "Moldova"
   ;; "Faroe Islands"
   ;; "Holy See (Vatican City)"
   "Czech Republic" "Czechia"})

;; TODO standardize country names across various sources
(def country-populations
  "From https://data.worldbank.org/indicator/SP.POP.TOTL
  NB: this dataset has some odd or crufty rows"
  (->> (csv/read-csv (slurp "resources/API_SP.POP.TOTL_DS2_en_csv_v2_821007.csv"))
       (drop 4) ;; first few rows are cruft
       (map (comp #(map string/trim %)
                  ;; country name is first column;
                  ;; 2018 data is third-to-last column
                  (juxt first (comp last butlast butlast))))
       (map (fn [[country pop-s]]
              [country (if (string/blank? pop-s)
                         0 ;; (Eritrea and "Not classified" have incomplete data)
                         (Long/parseLong (string/replace pop-s "," "")))]))
       (into {})))
