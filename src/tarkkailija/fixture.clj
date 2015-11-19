(ns tarkkailija.fixture
  (:use clojure.tools.logging)
  (:require [sade.mongo :as mongo]
            [tarkkailija.mongo]))

(def verttis-apikey "5087ba34c2e667024fbd5992")
(def perttis-apikey "5087ba34c2e667024fbd5993")

(def users
  [{:firstName "Vertti"
    :lastName "Kuumotus!"
    :enabled true
    :email "vertti@sertti.com"
    :password "jeejee"
    :private {:apikey verttis-apikey}}
   {:firstName "Pertti"
    :lastName "Polte"
    :enabled true
    :email "pertti@sertti.com"
    :password "jeejee"
    :private {:apikey perttis-apikey}}])

(defn apply! []
  (mongo/connect!)
  (warn "*** Clearing mongo! ***")
  (mongo/clear!)
  (dorun (map #(mongo/insert-one :users %) users))
  (info "Created " users " to mongo"))
