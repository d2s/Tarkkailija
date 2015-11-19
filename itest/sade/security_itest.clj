(ns sade.security-itest
  (:use midje.sweet
        sade.security
        tarkkailija.iftest.util
        sade.email)
  (:require [sade.mongo :as mongo]))

(with-server
  (connect-test-mongo)
  
  (fact "activation email is sent and appropriate data is persisted"
    (with-redefs-fn {#'sade.email/send-mail (fn [& _] {:result :sent})}
      #(let [user       (create-user {:firstName "Foo" :lastName "Bar" :email "foo.bar@foo.com" :password "passuPASSU"})
             from      "admin@sade.fi"
             host-url  "http://localhost:8080/"
             resp      (send-activation-mail-for {:user user :from from :host-url host-url})]
         (mongo/find-one :activation-emails {:activation-key (:key resp)}) =not=> nil?))))
