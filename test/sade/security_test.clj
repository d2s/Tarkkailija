(ns sade.security-test
  (:use clojure.test
        midje.sweet
        sade.security))

(facts "non-private"
  (fact "strips away private keys from map"
    (non-private {:email "teemu.testaaja@solita.fi" :private {:secret "1234"}}) => {:email "teemu.testaaja@solita.fi"})
  (fact ".. but not non-recursively"
    (non-private {:email "teemu.testaaja@solita.fi" :child {:private {:secret "1234"}}}) => {:email "teemu.testaaja@solita.fi" :child {:private {:secret "1234"}}}))

(let [user {:_id "1"
            :username  "simo@salminen.com"
            :role "comedian"
            :private "SECRET"}]
  (facts "summary"
    (fact (summary nil) => nil)
    (fact (summary user) => (just (dissoc user :private)))))
