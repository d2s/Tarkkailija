(ns tarkkailija.feed-test
  (:use clojure.test
        midje.sweet
        sade.core
        tarkkailija.env
        monger.operators
        tarkkailija.feed)
  (:require [clj-time.core :as time]
            [sade.mongo :as mongo]
            [sade.client :as client]
            [sade.email :as email]
            [tarkkailija.leiki :as leiki]))

(facts
  (against-background
     (now) => 123
     (mongo/find-many :watches {:emailChecked true :last_sent {$lt (- 123 feed-buffer-in-millis)}}) => [{:_id 1 :feed 1 :email :email1} {:_id 2 :feed 2 :email :email2}]
     (leiki/find-similar-content 1 :startdate (time/date-time 1969 12 31) :enddate (time/date-time 1970 01 01)) => [{:headline "otsikko1"}]
     (leiki/find-similar-content 2 :startdate (time/date-time 1969 12 31) :enddate (time/date-time 1970 01 01)) => [{:headline "otsikko2"}]
     (mongo/update-one :watches {:_id 1} {$set {:last_sent 123}}) => true
     (mongo/update-one :watches {:_id 2} {$set {:last_sent 123}}) => true
     (feed-mail [{:headline "otsikko1"}] (client/uri)) => :content
     (feed-mail [{:headline "otsikko2"}] (client/uri)) => :content2
     (email/send-mail :email1 email-from "Tarkkailija: sinulla on uusia ilmoituksia" :content) => {:result :sent}
     (email/send-mail :email2 email-from "Tarkkailija: sinulla on uusia ilmoituksia" :content2) => {:result :sent})
  (send-emails) => 2)
