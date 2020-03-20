(ns appliedsciencestudio.covid19-clj-viz.deutschland
  (:require [meta-csv.core :as mcsv]
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
  (reduce (fn [m {:keys [state latest-population]}]
            (assoc m state latest-population))
          {}
          (mcsv/read-csv "resources/deutschland.state-population.tsv"
                         {:header? true
                          :fields [{:field :state
                                    :postprocess-fn #(get normalize-bundesland % %)}
                                   nil nil nil nil nil nil nil
                                   {:field :latest-population
                                    :type :int
                                    :preprocess-fn (fn [^String s] (-> s
                                                                      string/trim
                                                                      (string/replace "," "")))}]})))

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
