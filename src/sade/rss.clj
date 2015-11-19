(ns sade.rss
  (:use clojure.data.xml))

(defn- get-elements [m]
  (map (fn [e]
         (let [f (first e)
               s (second e)]
           (element f {} (if (map? s) (get-elements s) s))))
       (for [[k v] m] [k v])))

(defn generate-rss [channel items]
  (emit-str (element :rss {:version "2.0" "xmlns:georss" "http://www.georss.org/georss"}
                     (element :channel {}
                              (get-elements channel)
                              (map #(element :item {} (get-elements %)) items)))))
