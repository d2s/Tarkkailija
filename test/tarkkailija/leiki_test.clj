(ns tarkkailija.leiki-test
  (:use clojure.test
        midje.sweet
        tarkkailija.leiki)
  (:require [sade.xml :as xml]
            [clj-time.core :as time]))

(facts "Time is parsed correctly"
  (let [date (->date "2012-11-15 23:59")]
    (time/year date) => 2012
    (time/month date) => 11
    (time/day date) => 15
    (time/hour date) => 23
    (time/minute date) => 59))

(facts "multiple valid locations are extracted correctly"
  (let [raw "<tag name=\"sys_f_location_point\">
               <value>306959.0 6750586.0</value>
               <value>306960.0 6750587.0</value>
               <value>0.0 0.0</value>
             </tag>"
        xml (xml/parse raw)]
    (->points xml) => [{:x "306959.0"
                        :y "6750586.0"}
                       {:x "306960.0"
                        :y "6750587.0"}]))

