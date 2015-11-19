(ns sade.core-test
  (:use clojure.test
        midje.sweet
        sade.email))

(facts
  (fact "successful email sending returns true with sent?"
    (sent? (send-mail :to :from :subject :html-msg)) => true
    (provided
      (send-mail :to :from :subject :html-msg) => {:result :sent}))

  (fact "failed email sending returns false with sent?"
    (sent? (send-mail :to :from :subject :html-msg)) => false
    (provided
      (send-mail :to :from :subject :html-msg) => {:result :error})))
