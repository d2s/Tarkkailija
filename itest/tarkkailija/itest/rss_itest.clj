(ns tarkkailija.itest.rss-itest
  (:use midje.sweet
        tarkkailija.rss
        tarkkailija.iftest.util)
  (:require [sade.client :as client]
            [sade.xml :as xml]
            [clj-http.client :as http]
            [tarkkailija.fixture :as fixture]))

(def apikey fixture/verttis-apikey)
(with-server
  (clear-db!)
  (fixture-minimal!)
  (fact "Can get RSS feed for a watch"
    (let [response (client/json-post
                     (client/uri "/api/watches")
                     {:name "Helsingin asemakaavat" :areas [{:id "category_all_na_1240"}] :email "test@test.com" :categories [{:id "category_search_na_23"}] :emailChecked false :publicFeed true :geoJson nil}
                     apikey)
          {{watch-id :_id} :watch} response]

      (let [xml (:body (http/get (get-rss-url watch-id) {:insecure? true}))]
        (:tag (xml/parse xml)) => :rss))))
