(ns tarkkailija.stest.leiki-stest
  (:require [clj-time.core :refer [now date-time ago years months]]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [tarkkailija.leiki :refer :all]
            [tarkkailija.iftest.util :as util]))

(util/with-server
  (util/clear-db!)

  (facts "feed mongering"
    (let [id (feed-id)
          areas      (map :value (area-search-with-text "Helsingin kaupunki" "fi"))
          categories (map :value (category-search "Rakentaminen" "fi"))
          geojson  nil]

      (fact "feed can be created"
        (add-or-update-content-item id areas categories geojson) => truthy)

      (fact "feed has content"
        (let [result (:articles (find-similar-content {:article-id id :startdate (date-time 2000) :enddate (date-time 2014 2 7)}))]
          (count result) => #(> % 0)))

      (fact "feed can be deleted"
        (remove-content-item id) => truthy)


      (fact "feed has content with pictures"
        (let [articles (:articles (find-similar-content-tarkkailija {:areas areas :categories categories :geojson geojson :startdate (-> 1 years ago)}))
              picture? #(:picture %)]
          (count articles) => #(> % 0)
          articles => (has every? picture?)))

      (fact "article limit and offset are working correctly in feed requ"
        (let [articles-first (:articles (find-similar-content-tarkkailija {:areas areas :categories categories :geojson geojson :offset 0 :limit 5 :startdate (date-time 2000) :enddate (date-time 2014 2 7)}))
              articles-second (:articles (find-similar-content-tarkkailija {:areas areas :categories categories :geojson geojson :offset 4 :limit 9 :startdate (date-time 2000) :enddate (date-time 2014 2 7)}))]
          (count articles-first) => 5
          (count articles-second) => 5
          (:id (last articles-first)) => (:id (first articles-second))))))



  (fact "frontpage categories have localized and capitalized names and proper id"
    (let [categories (frontpage-categories)
          ids (map :id categories)
          names (map :names categories)]
      ids => (has every? #(not (nil? %)))
      (doseq [e names] (keys e) => (just #{:fi :sv :en}))
      (doseq [e names] (vals e) => (has every? #(Character/isUpperCase (first %))))))

  (fact "category search returns proper id value pairs"
    (let [result (category-search "veneily" "fi")]
      (doseq [e result]
        (:value e) => truthy
        (:label e) => truthy)
      (map :label result) => (contains "Veneily")))

  (fact "area search returns proper id value pairs"
    (let [result (area-search-with-text "helsinki" "fi")]
      (doseq [e result]
        (:value e) => truthy
        (:label e) => truthy
        (:label e) => (contains "Helsin"))))

  (fact "area search with geojson parameter returns proper id value pairs"
    (let [result (area-search-with-geojson "{\"type\":\"Polygon\",\"coordinates\":[[[327556.43384803,6822558.3165211],[327556.43384803,6822670.3165211],[327768.43384803,6822670.3165211],[327768.43384803,6822558.3165211],[327556.43384803,6822558.3165211]]]}" "fi")]
      (count result) => 1
      (let [area (first result)]
        (:value area) => truthy
        (:label area) => truthy
        (:label area) => (contains "33100 tampere"))))

  (fact "average profile returns string based id and name and Long based weight"
    (let [data (get-average-profile (-> 1 months ago) (now) "fi")]
      (> (count data) 0) => true
      (doseq [d data] (:id d) => truthy)
      (doseq [d data] (:name d) => truthy)
      (doseq [d data] (type (:weight d)) => java.lang.Long))))
