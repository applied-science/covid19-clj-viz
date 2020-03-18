(ns appliedsciencestudio.covid19-clj-viz.bno
  "Data from BNO news
  https://bnonews.com/index.php/2020/02/the-latest-coronavirus-cases/"
  (:require [clojure.data.csv :as csv]
            [clojure.string :as string]))

(defn s->num [s]
  (if (or (string/blank? s)
          (= "-" s))
    0
    (Integer/parseInt (string/replace s "," ""))))

(def cases
  (let [[_ & rows] (csv/read-csv (slurp "resources/bno-cases.tsv")
                                 :separator \tab)
        header [:country :cases :deaths :serious :critical :recovered :source :unknown]]
    (->> (map zipmap
              (repeat header)
              rows)
         (map (fn [m] (-> m
                         (dissoc :unknown :source)
                         (update :cases s->num)
                         (update :serious s->num)
                         (update :critical s->num)
                         (update :deaths s->num)
                         (update :recovered s->num)))))))

(comment

  (sort-by :deaths > cases)
  ;; NB: Germany under-reports its COVID19 deaths by not testing
  ;; people who died with flu-like symptoms, so it _should_ be earlier
  ;; in this list.


  (sort-by :cases > cases)
  ;; NB: testing is not consistent across regions or countries

  )
