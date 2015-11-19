(ns tarkkailija.feed
  (:use hiccup.core
        sade.core
        sade.web
        tarkkailija.env
        monger.operators)
  (:require [sade.mongo :as mongo]
            [sade.client :as client]
            [clj-time.coerce :as coerce]
            [clj-time.core :as time]
            [tarkkailija.leiki :as leiki]
            [sade.email :as email]))

;; send-delay of 5sec
(def feed-buffer-in-millis (* 5 1000))

(defn enddate [millis] (-> millis coerce/from-long .toDateMidnight))
(defn startdate [millis] (-> millis enddate (.minusDays 1)))

(def ^:private feed-css
"
html {
  background-color: #f0f0f0;
}
body {
  font-family: Arial,FreeSans,Helvetica,sans-serif;
  font-size: 12pt;
  padding: 3em;
  margin: 1em;
  border: 2px solid grey;
  background-color: #ffffff;
}

h1 {
  font-size: 1.3em;
}

p {
  font-size: 1.0em;
}
")

(defn date [{d :date}]
  (let [dt (leiki/parse-date d)]
    (format "%s/%s/%s" (time/day dt) (time/month dt) (time/year dt))))

(defn feed-mail [articles host-url]
  (html [:html
         [:head
          [:base {:href host-url}]
          [:style {:type "text/css"} feed-css]]
         [:body
          [:h1 "Uudet ilmoitukset"]
          [:ul (for [article articles]
                 [:li (:headline article) " (" (date article) ")"])]
          [:a {:href "/app/index.html#/search/"} "Katsele ilmoituksia verkossa"]]]))

(defn email-feed [{:keys [_id feed email] :as watch}]
  (let [time      (now)
        articles  (leiki/find-similar-content feed :startdate (startdate time) :enddate (enddate time))
        host-url  (client/uri)
        message   (feed-mail articles host-url)
        subject   "Tarkkailija: sinulla on uusia ilmoituksia"
        sent      (email/sent? (email/send-mail email email-from subject message))]
    (if sent
      (do (mongo/update-one :watches {:_id _id} {$set {:last_sent time}}) 1)
      0)))

(defn send-emails
  "send emails and returns number of emails sent"
  []
  (let [time    (now)
        watches (mongo/find-many :watches {:emailChecked true :last_sent {$lt (- time feed-buffer-in-millis)}})]
       (reduce + (map email-feed watches))))

(when (dev-mode?)
  (defjson "/dev/admin/api/feeds" []
    (ok :emails (send-emails))))
