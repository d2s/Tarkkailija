(ns tarkkailija.domain-test
  (:use clojure.test
        midje.sweet
        tarkkailija.domain))

(facts
  (fetch-keywords {}) => #{}
  (fetch-keywords {:a true}) => #{:a}
  (fetch-keywords {:a true :b false}) => #{:a}
  (fetch-keywords {:a true :b false :c true}) => #{:a :c})
