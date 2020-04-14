(ns applied-science.covid19-clj-viz.deutschland
  (:require [applied-science.covid19-clj-viz.common :refer [applied-science-palette
                                                            vega-lite-config]]
            [applied-science.waqi :as waqi]
            [clojure.string :as string]
            [hickory.core :as hick]
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

;;;; Scraping tools
(defn deepest-content
  "Drill down to the deepest content node."
  [node]
  (if-let [content (or (:content node) (:content (first node)))]
    (deepest-content content)
    (if (vector? node)
      (apply str node)
      node)))

(defn nonneg-min
  "Returns least non-negative number from given `nums`, else `nil`."
  [& nums]
  (let [pos-nums (remove neg? nums)]
    (when (seq pos-nums)
      (apply min pos-nums))))

(defn nix-parentheticals [s]
  (if (string? s)
    (subs s 0 (if-let [i (nonneg-min (.indexOf s "[")
                                     (.indexOf s "("))]
                i
                (count s)))
    s))

(comment  
  (nix-parentheticals "Date")
  
  (nix-parentheticals "Germany, repatriated[c]")

  (nix-parentheticals "484 (2)")

  (nix-parentheticals "[d]")
  
  (nix-parentheticals "9794 [h] (101)")
  
  )

(defn deepest-text
  "Drill down to the deepest text node(s) and return them as a string."
  [node]
  (cond (vector? node) (-> (apply str (mapcat deepest-text node))
                           (string/replace " " "")
                           string/trim)
        (map? node) (deepest-text (:content node))
        :else node))

(defn extract-tables [page]
  (mapv (fn [table]
          (into [] (map (fn [row]
                          (mapv deepest-text (s/select (s/or (s/tag :th) (s/tag :td)) row)))
                        (s/select (s/tag :tr) table))))
        (s/select (s/tag :table) page)))

;;;; Wikipedia table clean-up utils
(defn wiki-date->utc [s]
  (let [[dd mm] (-> s
                    string/lower-case
                    (string/split #"\s"))]
    (string/join "-" ["2020"
                      ({"jan" "01"
                        "feb" "02"
                        "mar" "03"
                        "apr" "04"} mm)
                      (format "%02d" (Integer/parseInt dd))])))

(defn wiki-table-elm->num [s]
  (if (or (= s "—")
          (string/blank? s))
    0
    (Integer/parseInt s)))

(def wiki-page
  "2020 coronavirus pandemic in Germany"
  (-> "https://en.m.wikipedia.org/wiki/2020_coronavirus_pandemic_in_Germany"
      slurp
      hick/parse
      hick/as-hickory))

(def wiki-cumulative-infections-table
  (apply map vector
         ;; "Confirmed cumulative infections" table
         (conj (butlast (drop 3 (nth (extract-tables wiki-page) 2)))
               ["Date"
                ;; Regions:
                "Baden-Württemberg" "Bavaria" "Berlin" "Brandenburg" "Bremen"
                "Hamburg" "Hesse" "Lower Saxony" "Mecklenburg-Vorpommern"
                "North Rhine-Westphalia" "Rhineland-Palatinate" "Saarland"
                "Saxony" "Saxony-Anhalt" "Schleswig-Holstein" "Thuringia"
                "Germany, repatriated"
                ;; Calculated/cumulative:
                "Total infections"
                "Total deaths"
                "New cases"
                "New deaths"])))

(def cumulative-infections
  "Mapping from German states (and calculated fields) to case numbers by date."
  (let [[hdr & rows] (map #(map (comp string/trim nix-parentheticals)
                                %) wiki-cumulative-infections-table)
        ;; We expect the header to be Stringy dates, except for the
        ;; first, which we expect to be misleadingly labeled "Date"
        ;; but to actually contain place names or descriptions of
        ;; calculated fields.
        clean-hdr (map wiki-date->utc (rest hdr))]
    (reduce (fn [m [label & row]]
              (assoc m (normalize-bundesland label) ;; NB labels also include calculated fields
                     (zipmap clean-hdr
                             (map wiki-table-elm->num row))))
            {} rows)))

(defn parenthetical-num-or-0 [s]
  (let [i1 (.indexOf s "(")
        i2 (.indexOf s ")")]
    (if (and (string? s) (pos? i1) (pos? i2))
      (Integer/parseInt (subs s (inc i1) i2))
      0)))

(def cumulative-deaths
  "Mapping from German states to number of coronavirus deaths, by date."
  (let [[hdr & rows] (conj (map #(conj (map parenthetical-num-or-0 (rest %))
                                       (first %))
                                (rest wiki-cumulative-infections-table))
                           (first wiki-cumulative-infections-table))
        ;; We expect the header to be Stringy dates, except for the
        ;; first, which we expect to be misleadingly labeled "Date"
        ;; but to actually contain place names or descriptions of
        ;; calculated fields.
        clean-hdr (conj (map wiki-date->utc (rest hdr))
                        :label)]
    (dissoc (reduce (fn [acc m]
                      (assoc acc (normalize-bundesland (:label m))
                             (dissoc m :label)))
                    {}
                    (map zipmap (repeat clean-hdr) rows))
            "New cases" "Total infections" "Total deaths" "New deaths")))

(comment
  (sort (keys cumulative-infections))
  ;; ("Baden-Württemberg" "Bavaria" "Berlin" "Brandenburg" "Bremen" "Germany, repatriated" "Hamburg" "Hesse" "Lower Saxony" "Mecklenburg-Vorpommern" "New cases" "New deaths" "North Rhine-Westphalia" "Rhineland-Palatinate" "Saarland" "Saxony" "Saxony-Anhalt" "Schleswig-Holstein" "Thuringia" "Total deaths" "Total infections")
  )

(defn cumulative-cases-in [place]
  (reduce (fn [acc [date cases]]
            (if (= :place date)
              acc
              (conj acc {:cases cases :date date
                         :place place})))
          []
          (get cumulative-infections place)))


;;;; ===========================================================================
;;;; Cases in Berlin over time
(comment ;; These visualizations are in a `comment` so that folks
         ;; working through `covid19-in-the-repl` don't see unrelated
         ;; visualizations first. Otherwise the standard in this repo
         ;; is to put `plot!` calls at the top level.
  
  (waqi/plot!
   (merge-with merge vega-lite-config
               {:title {:text "Cases in selected German states over time"}
                :width 1200 :height 700
                :data {:values #_(cumulative-cases-in "Total infections")
                       (concat (cumulative-cases-in "Bayern")
                               (cumulative-cases-in "Nordrhein-Westfalen")
                               (cumulative-cases-in "Berlin"))}
                :mark {:type "line" :strokeWidth 4 :point "transparent"
                       :color (:purple applied-science-palette)}
                :encoding {:x {:field "date" :type "temporal"}
                           :y {:field "cases", :type "quantitative"}
                           :tooltip {:field "cases", :type "quantitative"}
                           :color {:field "place" :type "ordinal"
                                   :scale {:range [(:green applied-science-palette)
                                                   (:purple applied-science-palette)
                                                   (:blue applied-science-palette)]}}}}))
  
  ;; TODO cases in Germany
  ;; TODO deaths in Berlin
  ;; TODO mark special dates, e.g. quarantine


;;;; ===========================================================================
;;;; Deaths in <PLACE> (e.g. Berlin) over time
  (waqi/plot!
   (merge-with merge vega-lite-config
               {:title {:text "Deaths in Berlin over time (log scale)"}
                :width 750 :height 700
                :data {:values (->> (get cumulative-deaths "Berlin"
                                         #_ "Bayern"
                                         #_"Nordrhein-Westfalen")
                                    (remove (comp zero? val))
                                    (map #(assoc {} :date (key %) :cases (Math/log10 (val %)))))}
                :mark {:type "line" :strokeWidth 4 :point "transparent"
                       :color (:green applied-science-palette)}
                :encoding {:x {:field "date" :type "temporal" :timeUnit "date"}
                           :y {:field "cases", :type "quantitative"}
                           :tooltip {:field "cases", :type "quantitative"}}}))


;;;; ===========================================================================
;;;; Deaths in Germany over time, log scale
  (waqi/plot!
   (merge-with merge vega-lite-config
               {:title {:text "Deaths in Berlin over time, log-scale"}
                :width 1200 :height 700
                :data {:values (->> (get cumulative-infections "Total deaths")
                                    (remove (comp zero? val))
                                    (map #(assoc {} :date (key %) :deaths (Math/log10 (val %)))))}
                :mark {:type "line" :strokeWidth 4 :point "transparent"
                       :color (:pink applied-science-palette)}
                :encoding {:x {:field "date" :type "temporal" :timeUnit "date"}
                           :y {:field "deaths", :type "quantitative"}
                           :tooltip {:field "deaths", :type "quantitative"}}}))

  )


;;;; Scraping case data for areas of Germany
(def covid-page
  "We want this data, but it's only published as HTML."
  (-> (slurp "https://www.citypopulation.de/en/germany/covid/")
      hick/parse
      hick/as-hickory))

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

(comment ;;;; Berlin-specific historical data from citypop
  (map (fn [[d n]]
         {:date (str "2020-" (subs (name d) 6))
          :cases (Integer/parseInt (string/replace n "," ""))
          :place "Berlin"})
       (dissoc (some (fn [m] (when (comp "Berlin" :name) m)) citypop-data)
               :name :state :kind :part-of))

  ;; I think I'll stop trying to get this data from citypop. It works
  ;; today but I'm not confident it will work without refactoring
  ;; tomorrow, since they changed the dates they report since the last
  ;; I checked. This does not inspire confidence.

  )


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

(def legacy-cases
  "COVID-19 cases in Germany as of 6 March 2020.
  Necessary because our other sources (including RKI) do not provide historical case data.
  Manually copied from https://en.wikipedia.org/wiki/2020_coronavirus_pandemic_in_Germany#Robert_Koch_Institute"
  (mcsv/read-csv "resources/deutschland.covid19cases.2020-03-04.csv"
                 {:field-names-fn {"Bundesland" :state
                                   "Fälle" :cases}}))

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
