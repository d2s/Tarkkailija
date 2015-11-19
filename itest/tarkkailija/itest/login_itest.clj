(ns tarkkailija.itest.login-itest
  (:use midje.sweet
        sade.client
        tarkkailija.iftest.util)
  (:require [clj-http.client :as client]))


(with-server
  (facts "Pertti registers and becomes a valid user"
    (clear-db!)
    
    (let [pertti   {:email "nil@solita.fi"
                    :password "jeejee"}
          username (:email pertti)
          password (:password pertti)
          email    (:email pertti)]

      (fact "Pertti registers"
        (let [response (json-post (uri "/api/users") pertti)
              ok       (:ok response)
              id       (:id response)]

          response => truthy
          ok => truthy
          id => truthy
          ))

      (fact "Pertti can be listed"
        (let [response (json-get (uri "/dev/api/users"))
              ok       (:ok response)
              users    (:users response)
              pertti?  #(-> % :email (= email))]

          response => truthy
          ok => truthy
          users => (has some pertti?)
          ))

      (fact "Pertti has activation-url and can activate his account"
        (let [response       (json-get (uri "/dev/api/fixture/activation-url/" email))
              activation-url (:activation-url response)
              activated      (client/get activation-url)]
          response => truthy
          activation-url => truthy
          activated => truthy))

      (fact "Pertti can log in"
        (let [response  (http-post (uri "/security/login") {:username username :password password})
              ok        (:ok response)
              user      (:user response)]

          response => truthy
          ok => truthy
          user => (contains (select-keys pertti [:firstName :lastName :email]))))
      )))
