(ns appliedsciencestudio.covid19-clj-viz.deutschland
  (:require [clojure.string :as string]
            [meta-csv.core :as mcsv]))

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
                   {:state-province bundesland
                    :population (population bundesland)
                    :cases cases
                    :cases-per-100k (double (/ cases (/ (population bundesland)
                                                        100000)))}))
          {}
          (dissoc cases "Gesamt")))

;;;; ===========================================================================
;;;; Let's use a new data source: https://www.citypopulation.de/en/germany/covid/

;; What caveats does it have?  --> "The case numbers differ from RKI
;; figures because official state information was used. Becauce these
;; numbers refer to different time of day, the comparability between
;; states is slightly reduced. Last Update: 2020-03-19 20:25."

(comment
  ;;;; Parse TSV
  
  ;; The orthodox Clojure approach is `clojure.data.csv`.
  (clojure.data.csv/read-csv (slurp "resources/deutschland-covid19.citypop.tsv")
                             :separator \tab)
  
  ;; This gives us a seq of vectors.

  ;; What we usually want is a seq of maps.
  ;; Per its README, which documents several important idioms:
  (defn csv-data->maps [csv-data]
    (map zipmap
         (->> (first csv-data) ;; First row is the header
              (map keyword) ;; Drop if you want string keys instead
              repeat)
	     (rest csv-data)))

  (take 5 (csv-data->maps (clojure.data.csv/read-csv (slurp "resources/deutschland-covid19.citypop.tsv")
                                              :separator \tab)))


  ;; `Clojure.data.csv` is solid, but I prefer Nils Grunwald's `meta-csv`:
  (take 3 (mcsv/read-csv "resources/deutschland-covid19.citypop.tsv"))

  )


(def hm-cases-csv
  "COVID-19 cases according to health ministries of German states."
  (mcsv/read-csv "resources/deutschland-covid19.citypop.tsv"
                 {:null #{"..."}
                  :fields [:name :status
                           "2020-02-29" "2020-03-04" "2020-03-08"
                           {:field "2020-03-12" :type :int} "2020-03-16" "2020-03-19"]}))

(comment
  ;;;; More looking
  (distinct (map :status hm-cases-csv))

  
  ;; Does it conform to my naming scheme?
  (set (distinct (map :name (filter (comp #{"State"} :status)
                                    hm-cases-csv))))

  
  ;; Here I realized the CSV is "divided" by state rows:
  (group-by :status hm-cases-csv)
  
  ;; We must be careful to preserve that information.
  )

(def hm-cases
  (reduce (fn [acc m]
            (case (:status m)
              ("County" "County-level City" "Region")
              (conj acc (assoc m :state (:state (last acc))))              
              "State"
              (conj acc (assoc m :state (:name m)))

              "Federal Republic"
              (conj acc m)))
          []
          hm-cases-csv))


(comment
  
  ;; I want to know more about Brandenburg, the state that surrounds but excludes Berlin.
  (->> hm-cases
       (filter (comp #{"Brandenburg"} :state))
       (remove (comp #{"State"} :status))
       (map (fn [m] {(:name m) (get m "2020-03-19")}))
       (apply merge))  

  ;; ...same, as datapoints for 
  (->> hm-cases
       (filter (comp #{"Brandenburg"} :state))
       (remove (comp #{"State"} :status))
       (sort-by #(get % "2020-03-19") >))


  ;; What about cases across Germany?
  
  )
