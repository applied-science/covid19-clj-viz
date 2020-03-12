(ns appliedsciencestudio.covid19-clj-viz.deutschland
  (:require [clojure.data.csv :as csv]
            [clojure.string :as string]
            [jsonista.core :as json]))

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

;; from https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Fallzahlen.html
(def cases
  (reduce (fn [acc [bundesland n]]
            (assoc acc (get normalize-bundesland bundesland bundesland) (Integer/parseInt n)))
          {}
          (rest (csv/read-csv (slurp #_"resources/deutschland.covid19cases.06-11-2020.tsv"
                                     "resources/deutschland.covid19cases.tsv")
                              :separator \tab))))

;; from https://en.m.wikipedia.org/wiki/List_of_German_states_by_population
(def population
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
          cases))

;;;; Detailed case data from https://github.com/iceweasel1/COVID-19-Germany
;; use it for Berlin first

;; TODO perhaps switch `cases` entirely to this source?
(def cases2
  (->> (csv/read-csv (slurp "resources/COVID-19-Germany/germany_with_source.csv"))
       rest
       (reduce (fn [acc [_ date fed-state district latitude longitude src bundesland n]]
                 (update-in acc [(get normalize-bundesland fed-state fed-state)
                                 district]
                            (fnil inc 0)))
               {})))


(comment

  ;; Berlin districts
  ;; from https://github.com/funkeinteraktiv/Berlin-Geodaten/blob/master/berlin_bezirke.topojson
  (->> (json/read-value (java.io.File. "resources/public/public/data/berlin-original.geo.json")
                        (json/object-mapper {:decode-key-fn true}))
       :objects
       :states
       :geometries
       (map (comp :id :properties))
       set)

  ;; compare with:
  (set (keys (get cases2 "Berlin")))

  ;; they seem to use the same naming scheme. as of today, Treptow-Koepenick (with umlaut) has no cases.

  )


(def population-berlin
  ;; from https://en.m.wikipedia.org/wiki/Boroughs_and_neighborhoods_of_Berlin
  (->> (csv/read-csv (slurp (str "resources/berlin-population.tsv"))
                     :separator \tab)
       rest ;; drop the column header line
       (map (juxt first second)) ;; only take the name of the district and 2010 population data:
       (map #(mapv string/trim %))
       (map (fn [[district pop-s]]
              [district (Integer/parseInt (string/replace pop-s "," ""))]))
       (into {})))

(def bezirk-data
  "Map from Berlin Bezirk (district) to population, cases, and cases-per-100k persons."
  (reduce (fn [acc [district cases]]
            (assoc acc
                   district
                   {:state-or-province district
                    :population (population-berlin district)
                    :cases cases
                    :cases-per-100k (double (/ cases (/ (population-berlin district)
                                                        100000)))}))
          {}
          (get cases2 "Berlin")))
