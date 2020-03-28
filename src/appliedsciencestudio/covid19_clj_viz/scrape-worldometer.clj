(ns appliedsciencestudio.covid19-clj-viz.repl
  (:require [clojure.string :as string]
            [hickory.core :as hick]
            [hickory.select :as s]
            [clj-http.client :as client]))

;;;; Scraping data
(def worldometers-page
  "We want this data, but it's only published as HTML."
  (-> (client/get "http://www.worldometers.info/coronavirus/")
      :body hick/parse
      hick/as-hickory))

(defn deepest-content
  "Drill down to the deepest content node."
  [node]
  (if-let [content (or (:content node) (:content (first node)))]
    (deepest-content content)
    (cond (vector? node) (apply str (filter string? node))
          (map? node) nil
          :else node)))

(def headers
  (->> (s/select (s/tag :thead) worldometers-page)
       first
       (s/select (s/tag :tr))
       first
       (s/select (s/tag :th))
       (map deepest-content)))

(def dataset
  (->> (s/select (s/tag :tbody) worldometers-page)
       first
       (s/select (s/tag :tr))
       (map (fn [row]
              (zipmap headers (map deepest-content (s/select (s/tag :td) row)))))))

