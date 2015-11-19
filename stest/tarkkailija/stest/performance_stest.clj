(ns tarkkailija.stest.performance-stest
  (:require [tarkkailija.leiki :as leiki]
            [tarkkailija.watch :as watch]
            [tarkkailija.batchrun :as batch]
            [sade.email :as email]
            [sade.security :as security]))

; This test namespace is NOT meant to be ran automatically, thus it is not a real test,
; run if you feel like it but change the email first

(defn create-test-user []
  (security/create-user {:email "pentti@example.com" :password "salakala" :enabled true}))

(defn create-test-watches [amount]
  (let [areas (leiki/area-search-with-text "Helsinki" "fi")
        cats  (leiki/frontpage-categories)]
    (map (fn [i] {:name (str "Watch " i)
                  :email "pentti@example.com"
                  :areas [(:value (first areas))]
                  :categories (remove nil? (take-nth i cats))
                  :publicFeed true
                  :geoJson nil}))))

(defn do-the-test []
  (require 'tarkkailija.logging)
  (sade.mongo/connect!)
  (sade.mongo/clear!)
  (let [user       (create-test-user)
        watch-maps (create-test-watches 100)
        watches    (map #(watch/create-watch (:_id user) %) watch-maps)]
     (println "Watches created, now adding subscribers")
     (doseq [w watches]
       (watch/add-subscriber (:_id w) user "pentti@example.com"))
     (println "Subscribers added, now proceeding to actual watch processing benchmark"))

  (let [now     (System/currentTimeMillis)
        results (batch/send-emails-for-all-watches)]
    (println (str "Sent " (count results) " mails in " (- (System/currentTimeMillis) now) " ms"))))
