(ns appliedsciencestudio.covid19-clj-viz.deutschland
  (:require [clojure.data.csv :as csv]
            [meta-csv.core :as mcsv]
            [clojure.string :as string]))

(def normalize-bundesland
  "Mappings to normalize English/German and typographic variation to standard German spelling.
  Made with nonce code (and some digital massage) from geoJSON and wikipedia data."
  {"Bavaria" "Bayern"
   "Hesse" "Hessen" 
   "Lower Saxony" "Niedersachsen"
   "North Rhine-Westphalia" "Nordrhein-Westfalen"
   "Rhineland-Palatinate" "Rheinland-Pfalz"
   "Saxony" "Sachsen"
   "Saxony-Anhalt" "Sachsen-Anhalt"
   "Schleswig Holstein" "Schleswig-Holstein"
   "Thuringia" "Thüringen"})

(def cases
  "Number of confirmed COVID-19 cases by German province (auf Deutsch).
  Source: Robert Koch Institute https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Fallzahlen.html"
  (reduce (fn [acc m]
            (assoc acc (get normalize-bundesland (:bundesland m) (:bundesland m))
                   (Integer/parseInt (string/replace (:count m) "." ""))))
          {}
          (mcsv/read-csv "resources/deutschland.covid19cases.tsv"
                         {:field-names-fn {"Bundesland" :bundesland
                                           "Anzahl" :count
                                           "Differenz zum Vortag" :difference-carried-forward
                                           "Erkr./ 100.000 Einw." :sick-per-100k-residents
                                           "Todesfälle" :deaths
                                           "Besonders betroffene Gebiete in Deutschland" :particularly-affected-areas}
                          :guess-types? false})))

(def population
  "Population of German states.
  Source: Wikipedia https://en.m.wikipedia.org/wiki/List_of_German_states_by_population"
  (->> (csv/read-csv (slurp (str "resources/deutschland.state-population.tsv"))
                     :separator \tab)
       rest ;; drop the column header line
       (map (juxt first last)) ;; only take the name of the state and 2018 population data:
       (map #(mapv string/trim %)) 
       (map (fn [[state pop-s]]
              [(get normalize-bundesland state state)
               (Integer/parseInt (string/replace pop-s "," ""))]))
       (into {})))

(def bundeslaender-data
  "Map from bundesland to population, cases, and cases-per-100k persons."
  (reduce (fn [acc [bundesland cases]]
            (assoc acc
                   bundesland
                   {:state-or-province bundesland
                    :population (population bundesland)
                    :cases cases
                    :cases-per-100k (double (/ cases (/ (population bundesland)
                                                        100000)))}))
          {}
          (dissoc cases "Gesamt")))
