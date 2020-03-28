(ns appliedsciencestudio.covid19-clj-viz.deutschland
  (:require [hickory.core :as hick]
            [hickory.select :as s]
            [meta-csv.core :as mcsv]))

(defn normalize-bundesland
  "Standardizes English/German & typographic variation in German state names to standard German spelling.
  Made with nonce code (and some digital massage) from geoJSON and wikipedia data."
  [bundesland]
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
                (when-let [converted (try (convert s) (catch Exception _ false))]
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
