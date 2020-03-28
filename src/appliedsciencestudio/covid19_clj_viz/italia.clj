(ns appliedsciencestudio.covid19-clj-viz.italia
  "Following Alan Marazzi's 'The Italian COVID-19 situation' [1] with orthodox Clojure rather than Panthera

  Run it in NextJournal at https://nextjournal.com/alan/getting-started-with-italian-data-on-covid-19

  [1] https://alanmarazzi.gitlab.io/blog/posts/2020-3-19-italy-covid/"
  (:require [clojure.data.csv :as csv]
            [meta-csv.core :as mcsv]
            [clojure.string :as string]))

;; We can get province data out of Italy's CSV data using the orthodox
;; Clojure approach, `clojure.data.csv`:
(def provinces
  (let [hdr [:date :state :region-code :region-name
             :province-code :province-name :province-abbrev
             :lat :lon :cases]
        rows (csv/read-csv (slurp "resources/Italia-COVID-19/dati-province/dpc-covid19-ita-province.csv"))]
    (map zipmap
         (repeat hdr)
         (rest rows))))

;; Or, we could use Nils Grunwald's `meta-csv`, which does the
;; automatic parsing that we often want.

;; First, an Italian-to-English translation mapping for header names.
(def fields-it->en
  {;; For provinces (and some for regions too)
   "data"                    :date
   "stato"                   :state
   "codice_regione"          :region-code
   "denominazione_regione"   :region-name
   "codice_provincia"        :province-code
   "denominazione_provincia" :province-name
   "sigla_provincia"         :province-abbreviation
   "lat"                     :lat
   "long"                    :lon
   "totale_casi"             :cases
   ;; For regions
   "ricoverati_con_sintomi"      :hospitalized
   "terapia_intensiva"           :icu
   "totale_ospedalizzati"        :tot-hospitalized
   "isolamento_domiciliare"      :quarantined
   "totale_attualmente_positivi" :tot-positives
   "nuovi_attualmente_positivi"  :new-positives
   "dimessi_guariti"             :recovered
   "deceduti"                    :dead
   "tamponi"                     :tests})

;; Now we can just read the CSV.
(def provinces2
  (mcsv/read-csv "resources/Italia-COVID-19/dati-province/dpc-covid19-ita-province-latest.csv"
                 {:field-names-fn fields-it->en}))

(comment
  ;;;; Let's examine the data.
  ;; Instead of `pt/names`:
  (keys (first provinces))

  (keys (first provinces2))


  ;;;; I often use an alternative approach when reading CSVs,
  ;;;; transforming rather than replacing the header:
  (let [[hdr & rows] (csv/read-csv (slurp "resources/Italia-COVID-19/dati-province/dpc-covid19-ita-province.csv"))]
    (map zipmap
         (repeat (map (comp keyword #(string/replace % "_" "-")) hdr))
         rows))q


  ;;;; Check the data
  ;; Do we have the right number of provinces?
  (count (distinct (map :province-name provinces))) ;; be sure to evaluate the inner forms as well

  ;; No, there's an extra "In fase di definizione/aggiornamento", or cases not attributed to a province.

  ;; Let's ignore those.
  (remove (comp #{"In fase di definizione/aggiornamento"} :province-name) provinces)

  )


;; Again, we can use the DIY approach with Clojure's standard CSV parser:
(def regions
  (let [hdr [:date :state :region-code :region-name :lat :lon
             :hospitalized :icu :total-hospitalized :quarantined
             :total-positives :new-positives :recovered :dead :tests]
        [_ & rows] (csv/read-csv (slurp "resources/Italia-COVID-19/dati-regioni/dpc-covid19-ita-regioni.csv"))]
    (->> (map zipmap (repeat hdr) rows)
         (map (comp (fn [m] (update m :quarantined #(Integer/parseInt %)))
                    (fn [m] (update m :hospitalized #(Integer/parseInt %)))
                    (fn [m] (update m :icu #(Integer/parseInt %)))
                    (fn [m] (update m :dead #(Integer/parseInt %)))
                    (fn [m] (update m :recovered #(Integer/parseInt %)))
                    (fn [m] (update m :total-hospitalized #(Integer/parseInt %)))
                    (fn [m] (update m :total-positives #(Integer/parseInt %)))
                    (fn [m] (update m :new-positives #(Integer/parseInt %)))
                    (fn [m] (update m :tests #(Integer/parseInt %))))))))


;;;; Calculate daily changes
;; Now we want to determine how many new tests were performed each
;; day, and the proportion that were positive.

;; The following is equivalent to `regions-tests`:
(def tests-by-date
  (reduce (fn [acc [date ms]]
            (conj acc {:date date
                       :tests (apply + (map :tests ms))
                       :new-positives (reduce + (map :new-positives ms))}))
          []
          (group-by :date regions)))

(comment
  ;; Take a quick look at the data
  (sort-by :date tests-by-date)

  ;; And calculate our result using Clojure's sequence & collection libraries:
  (reduce (fn [acc [m1 m2]]
            (conj acc (let [daily-tests (- (:tests m2) (:tests m1))]
                        (assoc m2
                               :daily-tests daily-tests
                               :new-by-test (double (/ (:new-positives m2)
                                                       daily-tests))))))
          []
          (partition 2 1 (conj (sort-by :date tests-by-date)
                               {:tests 0})))

  )

(defn normalize-region-names
  "These region names a different on the population data files, geo.json files, and the covid-19 files."
  [region]
  (get
   {"Friuli Venezia Giulia" "Friuli-Venezia Giulia"
    "Emilia Romagna" "Emilia-Romagna"
    "Valle d'Aosta" "Valle d'Aosta/Vallée d'Aoste"} region))

(defn normalize-trentino
  "The COVID numbers from Italy split one region into two. 'P.A. Bolzano' and 'P.A. Trento' should be combined into 'Trentino-Alto Adige/Südtirol.'"
  [region-covid-data]
  (let [keys-to-sum [:hospitalized :icu :tot-hospitalized :quarantined :tot-positives :new-positives :recovered :dead :cases :tests]
        regions-to-combine (filter #(or (= "P.A. Bolzano" (:region-name %)) (= "P.A. Trento" (:region-name %))) region-covid-data)
        name-combined-region (fn [combined-regions] (assoc combined-regions :region-name "Trentino-Alto Adige/Südtirol"))
        remove-inexact-data (fn [combined-regions] (dissoc combined-regions :lat :lon))]
    (->> (map #(select-keys % keys-to-sum) regions-to-combine)
         (reduce #(merge-with + %1 %2))
         (conj (first regions-to-combine))
         (name-combined-region)
         (remove-inexact-data)
         (conj region-covid-data))))

(def region-covid-data
  (->> (mcsv/read-csv "resources/Italia-COVID-19/dati-regioni/dpc-covid19-ita-regioni-latest.csv"
                      {:field-names-fn fields-it->en})
       (map #(update % :region-name (fn [region-name]
                                      (if-let [update-region-name (normalize-region-names region-name)]
                                        update-region-name
                                        region-name))))
       (normalize-trentino)))

(defn conform-to-territory-name
  "Index each map of territory information by territory name."
  [territories territory-key]
  (->> (map #(vector (territory-key %) %) territories)
       (into {})))

(defn compute-cases-per-100k [province-data-with-pop]
  (map #(let [cases (% :cases)
              population (% :population)
              calc-cases (fn [x] (double (/ cases x)))
              per-100k (fn [x] (/ x 100000))]
          (->> (if population ((comp calc-cases per-100k) population) 0) ;; TODO: change from nil
               (assoc % :cases-per-100k))) province-data-with-pop))

(def region-population-data
  "From http://www.comuni-italiani.it/province.html with updates to Trentino-Alto Adige/Südtirol and Valle d'Aosta/Vallée d'Aoste."
  (-> (mcsv/read-csv "resources/italy.region-population.csv" {:fields [:region-name :population :number-of-provinces]})
      (conform-to-territory-name :region-name)))

(def province-populations
  "From http://www.comuni-italiani.it/province.html. Italy changed how provinces are structured in Sardina in 2016.
   Some are manually updated using the data here: https://en.wikipedia.org/wiki/Provinces_of_Italy"
  (-> (mcsv/read-csv "resources/italy.province-population.csv" {:fields [:province-name :population :abbreviation]})
      (conform-to-territory-name :province-name)))

(defn add-population-to-territories [all-territory-data all-territory-population territory-key]
  (map #(let [territory-to-update (% territory-key)]
          (->> (all-territory-population territory-to-update)
               (:population)
               (assoc % :population))) all-territory-data))

(def region-data "For use with resources/public/public/data/limits_IT_regions-original.geo.json"
  (-> (add-population-to-territories region-covid-data region-population-data :region-name)
      (compute-cases-per-100k)
      (conform-to-territory-name :region-name)))

(def province-data
  "For use with resources/public/public/data/limits_IT_provinces-original.geo.json"
  (-> (remove (comp #{"In fase di definizione/aggiornamento"} :province-name) provinces2)
                       (add-population-to-territories province-populations :province-name)
                       (compute-cases-per-100k)
                       (conform-to-territory-name :province-name)))
