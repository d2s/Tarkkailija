(ns tarkkailija.migration.21052013
  (:use [monger.operators])
  (:require [sade.mongo :as m]))

(defn migrate []
  (doseq [doc (m/find-many :watches {:categories {$in [#"(?s)category_search_na_.*"]}})]
    (m/update-one-and-return 
      :watches 
      {:_id (:_id doc)}
      {$set {:categories (map (fn [e] (clojure.string/replace e #"category_search_na" "category_all_na")) (:categories doc))}})))

{:schema "1.0" :script migrate}