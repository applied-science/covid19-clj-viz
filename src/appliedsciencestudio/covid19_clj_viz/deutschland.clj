(ns appliedsciencestudio.covid19-clj-viz.deutschland
  (:require [clojure.string :as string]
            [hickory.core :as hick]
            [hickory.select :as s]
            [meta-csv.core :as mcsv]))

(defn normalize-bundesland [bundesland]
  "Standardizes English/German & typographic variation in German state names to standard German spelling.
  Made with nonce code (and some digital massage) from geoJSON and wikipedia data."
  (get {"Bavaria" "Bayern"
        "Hesse" "Hessen"
        "Lower Saxony" "Niedersachsen"
        "North Rhine-Westphalia" "Nordrhein-Westfalen"
        "Rhineland-Palatinate" "Rheinland-Pfalz"
        "Saxony" "Sachsen"
        "Saxony-Anhalt" "Sachsen-Anhalt"
        "Schleswig Holstein" "Schleswig-Holstein"
        "Thuringia" "Thüringen"}
       bundesland bundesland))

;;;; Scraping case data for areas of Germany
(def covid-page
  "We want this data, but it's only published as HTML."
  (-> (slurp "https://www.citypopulation.de/en/germany/covid/")
      hick/parse
      hick/as-hickory))

(defn deepest-content
  "Drill down to the deepest content node."
  [node]
  (if-let [content (or (:content node) (:content (first node)))]
    (deepest-content content)
    (if (vector? node)
      (apply str node)
      node)))

(def citypop-data
  "COVID19 case data by date and region, from citypopulation.com"
  (let [fields [:name :kind :cases-02-29 :cases-03-04 :cases-03-08 :cases-03-12 :cases-03-16 :cases-03-20]]
    (mapcat (fn [[state counties]]
              (let [state-name (-> (s/select (s/attr :data-wiki) state) first :attrs :data-wiki)]
                (map (fn [county]
                       (assoc
                        (zipmap fields
                                (butlast (map deepest-content (s/select (s/tag :td) county))))
                        :part-of state-name))
                     (s/select (s/tag :tr) counties))))
            (butlast (partition 2 (s/select (s/tag :tbody) covid-page))))))

;;;; CSV parsing
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
  "Map from Bundesland (German state) to case data.
   Case data: cases, cases per 100.000 inhabitants, deaths, increase from previous report.
   Source: Robert Koch Institute https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Fallzahlen.html"
  (->> (mcsv/read-csv "resources/deutschland.covid19cases.tsv"
                      {:field-names-fn {"Bundesland" :bundesland
                                        "Anzahl" :cases
                                        "Differenz zum Vortag" :difference-carried-forward
                                        "Erkr./ 100.000 Einw." :cases-per-100k
                                        "Todesfälle" :deaths
                                        "Besonders betroffene Gebiete in Deutschland" :particularly-affected-areas}
                       :guess-types? false})
       (mapv #(let [normed-bundesland (normalize-bundesland (:bundesland %))]
                (vector normed-bundesland
                        (reduce (fn [m k] (update m k parse-german-number)) % (keys %)))))
       (into {})))

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
