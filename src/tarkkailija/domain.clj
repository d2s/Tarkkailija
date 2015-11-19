(ns tarkkailija.domain)

(defn fetch-keywords [m]
   (or (set (keys (filter #(-> % val true?) m))) #{}))
