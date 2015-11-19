(ns tarkkailija.itest.watch-itest
  (:use [midje.sweet]
        [sade.client]
        [tarkkailija.iftest.util]
        [tarkkailija.leiki]
        [tarkkailija.watch])
  (:require [sade.mongo :as mongo]
            [tarkkailija.mongo]
            [sade.client :as client]
            [clj-http.client :as http]
            [tarkkailija.fixture :as fixture]))

;; Requires server & mongo to be running

(def vertti-apikey fixture/verttis-apikey)
(def pertti-apikey fixture/perttis-apikey)

(with-server
  (clear-db!)
  (fixture-minimal!)

  (facts
    "Basic watch operations"

    (let [area             (:value (first (area-search-with-text "Helsinki" "fi")))
          category         (:value (first (category-search "Kaavoitus" "fi")))
          another-category (:value (first (category-search "Veneily" "fi")))]
      (fact
        "Vertti has no watches"|
        (let [response (client/http-get (uri "/api/watches") vertti-apikey)
              {:keys [ok watches]} response]
          ok => truthy
          (empty? watches) => truthy))

      (fact
        "Vertti creates watch"
        (let [response (client/json-post  (uri "/api/watches")
                                          {:name "Nimi" :areas [area] :email "test@test.com" :categories [category] :emailChecked false :publicFeed true :geoJson "%7B%22type%22%3A%22Polygon%22%2C%22coordinates%22%3A%5B%5B%5B316500.5%2C6744569%5D%2C%5B316500.5%2C6746169%5D%2C%5B318800.5%2C6746169%5D%2C%5B318800.5%2C6744569%5D%2C%5B316500.5%2C6744569%5D%5D%5D%7D"}
                                          vertti-apikey)
              {ok :ok {:keys [_id name areas email categories emailChecked rssLink geoJson]} :watch} response]
          ok => true
          name => "Nimi"
          areas => [area]
          email => "test@test.com"
          categories => [category]
          geoJson => "%7B%22type%22%3A%22Polygon%22%2C%22coordinates%22%3A%5B%5B%5B316500.5%2C6744569%5D%2C%5B316500.5%2C6746169%5D%2C%5B318800.5%2C6746169%5D%2C%5B318800.5%2C6744569%5D%2C%5B316500.5%2C6744569%5D%5D%5D%7D"))

      (fact
        "Vertti has now one watch"
        (let [response (client/http-get (uri "/api/watches") vertti-apikey)
              {:keys [ok watches]} response]
          ok => truthy
          (count watches) => 1))

      (fact
        "Vertti can happily edit his watch"
        (let [response (client/http-get (uri "/api/watches") vertti-apikey)
              watch (clojure.set/rename-keys (-> response :watches first) {:_id :id})]
          (let [response (client/json-put (uri "/api/watches")
                                          (assoc watch :categories [another-category])
                                          vertti-apikey)
                {ok :ok {:keys [name categories]} :watch} (parse-body response)]
            ok => truthy
            name => "Nimi"
            categories => [another-category])))

      (fact
        "Vertti creates another watch and then has two watches"
        (client/json-post (uri "/api/watches")
                          {:name "Toinen Nimi" :areas [area] :email "test@test.com" :categories [another-category] :emailChecked false :publicFeed true :geoJson nil}
                          vertti-apikey)
        (let [response (client/http-get (uri "/api/watches") vertti-apikey)
              {:keys [ok watches]} response]
          ok => truthy
          (count watches) => 2))

      (fact
        "Now we can retrieve articles for two public watches"
        (let [response (client/http-get (uri "/api/public/watches/limit/2") vertti-apikey)
              {:keys [ok watches]} response]
          ok => true
          (count watches) => 2
          (let [{:keys [watch articles]} (first watches)]
            (:publicFeed watch) => true)))

      (fact
        "Vertti creates a private watch, which is not visible to public"
        (let [resp (client/json-post (uri "/api/watches")
                                     {:name "Nimi" :areas [{:id area}] :email "test@test.com" :categories [{:id another-category}] :emailChecked false :publicFeed false :geoJson nil}
                                     vertti-apikey)
              {ok :ok {id :_id} :watch} resp]
          (let [success (client/plain-get (uri "/api/watches/" id) vertti-apikey)
                error (http/get (uri "/api/watches/" id) {:throw-exceptions false :insecure? true})
                {ok :status} success
                {authRequired :status} error]
            ok => 200
            authRequired => 401)))

      (fact
        "Vertti can even delete one of his watches"
        (let [response (client/http-get (uri "/api/watches") vertti-apikey)
              id (-> response :watches first :_id)
              ok (-> (client/http-delete (uri (str "/api/watches/" id)) vertti-apikey) parse-body :ok) ]
          ok => true))))



  (facts
    "Watch subscription tests"

    (let [response (client/http-get (uri "/api/watches") vertti-apikey)
          {:keys [ok watches]} response
          correctWatchId (:_id (first watches))
          falseWatchId "123456789"
          lang "fi"
          testEmail_1 "foofbaros_1@foofbaros.fi"
          testEmail_2 "foofbaros_2@foofbaros.fi"
          testEmail_3 "foofbaros_3@foofbaros.fi"]

      (fact
        "Vertti subscribes the watch to his email"
        (let [errorResp (client/json-put (uri "/api/subscribes") {:watchId falseWatchId :email testEmail_1 :lang lang} vertti-apikey)
              successResp (parse-body (client/json-put (uri "/api/subscribes") {:watchId correctWatchId :email testEmail_1} vertti-apikey))]
          ok => truthy
          (:status errorResp) => 404
          (:ok successResp) => true

          (let [ok (-> (client/json-put (uri "/api/subscribes") {:watchId correctWatchId :email testEmail_2 :lang lang} vertti-apikey) parse-body :ok)]
            ok => true

            (let [emails (map :email (get-email-subscriptions-of-watch correctWatchId))]
              emails => truthy
              (count emails) => 1
              (first emails) => testEmail_2))))

      (fact
        "Pertti subscribes the same watch to his email"
        (let [successResp (parse-body (client/json-put (uri "/api/subscribes") {:watchId correctWatchId :email testEmail_3 :lang lang} pertti-apikey))]
          (:ok successResp) => true

          (let [emails (map :email (get-email-subscriptions-of-watch correctWatchId))]
            emails => truthy
            (count emails) => 2
            (some #(= testEmail_2 %) emails) => true
            (some #(= testEmail_3 %) emails) => true)))

      (fact
        "Delete email subscriptions from watch"
        (let [ok1 (-> (client/http-delete (uri (str "/api/subscribes/" correctWatchId)) vertti-apikey) parse-body :ok)
                    ok2 (-> (client/http-delete (uri (str "/api/subscribes/" correctWatchId)) pertti-apikey) parse-body :ok)
                    emails (vals (get-email-subscriptions-of-watch correctWatchId))]
          ok1 => true
          ok2 => true
          (empty? emails) => true))

      (fact
        "Vertti subscribes to his own watch with default email"
        (let [ok (:ok (parse-body (client/json-put (uri "/api/subscribes") {:watchId correctWatchId :email nil :lang lang} vertti-apikey)))
              db-subscriptions (get-email-subscriptions-of-watch correctWatchId)]
          ok => true

          (first db-subscriptions) => truthy
          (:email (first db-subscriptions)) => "vertti@sertti.com"))

      (fact
        "Pertti subscribes with custom email and now subscription retrieval returns Vertti's original email and Pertti's custom email"
        (let [ok (:ok (parse-body (client/json-put (uri "/api/subscribes") {:watchId correctWatchId :email testEmail_1 :lang lang} pertti-apikey)))
              db-subscriptions (get-email-subscriptions-of-watch correctWatchId)]
          ok => true
          (map :email db-subscriptions) => (just #{testEmail_1 "vertti@sertti.com"})))

      (fact
        "Pertti changes his subscription language to swedish"
        (let [ok (:ok (parse-body (client/json-put (uri "/api/subscribes") {:watchId correctWatchId :email testEmail_1 :lang "sv"} pertti-apikey)))
              db-subscriptions (get-email-subscriptions-of-watch correctWatchId)]
          ok => true
          (count db-subscriptions) => 2
          (map :email db-subscriptions) => (just #{testEmail_1 "vertti@sertti.com"})
          (map :lang db-subscriptions) => (just #{"fi" "sv"}))))))
