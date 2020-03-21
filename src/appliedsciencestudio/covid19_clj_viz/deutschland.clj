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

(def population
  "Population of German states.
  Source: Wikipedia https://en.m.wikipedia.org/wiki/List_of_German_states_by_population"
  (->> (mcsv/read-csv "resources/deutschland.state-population.tsv"
                      {:header? true
                       :field-names-fn (comp keyword string/lower-case)})
       (reduce (fn [m {state :state population :2018}]
                 (assoc m
                        (get normalize-bundesland state state)
                        (Integer/parseInt (string/replace population "," ""))))
               {})))

(defn coerce-type-from-string
  "Simplest possible type guessing, obviously not production-grade
  heuristics."
  [s]
  (or (reduce (fn [_ convert]
                (when-let [converted (try (convert s) (catch Exception e false))]
                  (reduced converted)))
              [#(or (nil? %) (empty? %)) nil
               #(Integer/parseInt %)
               #(Float/parseFloat %)])
      s))

(defn parse-german-number [s]
  (if (string? s)
    (-> (.replace s "+" "")
        (.replace "." "")
        (.replace "," ".")
        coerce-type-from-string)
    s))

(def bundeslaender-data
  "Number of confirmed COVID-19 cases by German province (auf Deutsch).
  Source: Robert Koch Institute https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Fallzahlen.html"
  (->> (mcsv/read-csv "resources/deutschland.covid19cases.tsv"
                      {:field-names-fn {"Bundesland" :bundesland
                                        "Anzahl" :cases
                                        "Differenz zum Vortag" :difference-carried-forward
                                        "Erkr./ 100.000 Einw." :cases-per-100k
                                        "Todesfälle" :deaths
                                        "Besonders betroffene Gebiete in Deutschland" :particularly-affected-areas}
                       :guess-types? false})
       (mapv #(let [normed-bundesland (normalize-bundesland (:bundesland %) (:bundesland %))]
                (vector normed-bundesland
                        (-> (reduce (fn [m k] (update m k parse-german-number)) % (keys %))
                            (assoc :population (population normed-bundesland))))))
       (into {})))

;; no longer needed because this information is also in the bundeslaender-data
(def cases
  (into {} (map (juxt :bundesland :cases) (vals bundeslaender-data))))
