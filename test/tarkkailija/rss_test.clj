(ns tarkkailija.rss-test
  (:use tarkkailija.rss
        clojure.test
        midje.sweet)
  (:require [sade.client :as client]))

(facts
  (fact
    "Leiki article is transformed to proper rss model"
    (get-item {:headline "otsikko" :description "sisältö" :link "linkki"}) => {:title "otsikko" :description "sisältö" :link "linkki" :guid "linkki"}
    (get-item {:headline "otsikko" :description "sisältö" :picture "kuvan urli" :link "linkki" :locationx "x" :locationy "y"}) => {:title "otsikko" :description "sisältö" :link "linkki" :guid "linkki" "georss:point" "x y"}
    (get-item {:headline "otsikko" :description "sisältö" :picture "kuvan urli" :date "2013-01-14T00:00:00" :link "linkki" :locationx "x" :locationy "y"}) => {:title "otsikko" :pubDate "2013-01-14T00:00:00" :description "sisältö" :link "linkki" :guid "linkki" "georss:point" "x y"})
  (fact
    "Can get proper link for rss with a watch"
    (let [server-address (client/server-address)
          watch-id "1234"]
      (get-rss-url watch-id) => (str server-address "/api/watches/" watch-id "/rss")))

  (let [watch {:_id "1234"
               :name "Pertin profiili"
               :areas [{:id "123123asdf" :name "Jokioinen"}
                       {:id "234q23sd" :name "Joroinen"}]
               :categories [{:id "123123sdfs"
                             :names {:fi "Kalastus" :se "Kalastus"}}
                            {:id "123123asdf"
                             :names {:fi "Hiihto" :se "Hiihto"}}]}

        articles [{:headline "otsikko"
                   :content "sisältö"
                   :picture "kuvan urli"
                   :link "linkki"
                   :locationx "x"
                   :locationy "y"}]]
    (fact
      "Can get rss feed with watch and related articles"
      (let [server-address (client/server-address)]
        (get-rss-feed watch articles) => truthy))

    (fact
      "Can get feed with watch that has some geoJson innit"
      (let [server-address (client/server-address)
            geojson "{\"type\":\"Polygon\",\"coordinates\":[[[290300.49816895,6735535.9993896],[290300.49816895,6772735.9993896],[357300.49816895,6772735.9993896],[357300.49816895,6735535.9993896],[290300.49816895,6735535.9993896]]]}"]
        (get-rss-feed (merge watch {:geoJson geojson}) articles) => truthy))))
