(ns tarkkailija.itest.browse-itest
  (:use midje.sweet
        sade.client
        tarkkailija.iftest.util)
  (:require [clojure.walk :as walk]
            [cheshire.core :as json]))

(with-server-db-cleared
  (defn generate-watches [amount type]
    (json-get (uri "/dev/api/fixture/users/create-random/" 1))
    (walk/keywordize-keys (:body (json-get (uri "/dev/api/fixture/watches/create-random/" amount) {:type (name type)}))))

  (facts "Public watch searches"
    (fact "No public watches listed with empty database"
      (let [result (json-get (uri "/api/public/watches") {:limit 10 :skip 0 :query ""})]
        (:ok result) => truthy
        (:total result) => 0))

    (fact "All public watches found when no query parameters"
      (generate-watches 10 :public)
      (let [result (json-get (uri "/api/public/watches") {:limit 10 :skip 0 :query ""})]
        (:ok result) => truthy
        (:total result) => 10))

    (fact "Only matching public watches found with query parameters"
      (let [expected (first (generate-watches 10 :public))
            result   (json-get (uri "/api/public/watches") {:limit 10 :skip 0 :query (:name expected)})]
        (:ok result) => truthy
        (:total result) => 10
        (:queryHits result) => 1
        ((comp :_id first :watches) result) => (:_id expected)))))
