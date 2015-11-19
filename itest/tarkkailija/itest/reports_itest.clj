(ns tarkkailija.itest.reports-itest
  (:use [midje.sweet]
        [tarkkailija.iftest.util])
  (:require [clj-time.core :as clj-time]
            [sade.security :as security]
            [sade.mongo :as mongo]
            [tarkkailija.reports :as reports]))

(def characters (map char (concat (range 48 58) (range 66 91) (range 97 123))))
(defn random-char []
  (nth characters (rand-int (- (count characters) 1))))
(defn random-string [length]
  (apply str (take length (repeatedly random-char))))
(defn rand-email []
  (str (random-string (+ 3 (rand-int 5))) "@" (random-string (+ 3 (rand-int 5))) ".fi"))

(defn create-test-users [^:org.joda.time.DateTime d ^:Integer a]
  (doall
    (map 
      (fn [_] 
        (binding [mongo/now (fn [] (.getMillis d))]
          (security/create-user
            {:email (rand-email)
             :password (random-string 10)
             :enabled true})))
      (range a))))

(defn pick-create [d]
  (first (map :_created d)))

(against-background [(before :contents (mongo/connect! (test-mongo-uri)))
                     (before :facts (mongo/clear!))]
  
  (fact "User reporting provides data as two-dimensional array with first element being timestamp"
    (let [old-users-date (pick-create (create-test-users (clj-time/date-time 2013 5 1) 2))
          lone-wolf-date (pick-create (create-test-users (clj-time/date-time 2013 5 2) 1))
          mid-users-date (pick-create (create-test-users (clj-time/date-time 2013 5 4) 3))
          new-users-date (pick-create (create-test-users (clj-time/date-time 2013 5 5) 5))]
      
      (reports/users-by-date old-users-date new-users-date) => [[old-users-date 2] 
                                                                [lone-wolf-date 1] 
                                                                [mid-users-date 3] 
                                                                [new-users-date 5]]))
  
  (fact "Users created at same day but different timestamp are grouped together by day"
    (let [d (clj-time/date-time 2013 1 1)
          first-date  (pick-create (create-test-users (clj-time/plus d (clj-time/hours 1)) 1))
          second-date (pick-create (create-test-users (clj-time/plus d (clj-time/hours 2)) 1))]
      
      (reports/users-by-date first-date second-date) => [[(.getMillis (reports/date-midnight-utc d)) 2]]))
  
  (fact "Results are limited by given time limits"
    (let [d (clj-time/date-time 2013 1 1)]
      (create-test-users d 1) ; included
      (create-test-users (clj-time/plus d (clj-time/days 7)) 1) ; excluded
      
      (reports/users-by-date 
        (.getMillis d)
        (.getMillis (clj-time/plus d (clj-time/days 6)))) => [[(.getMillis d) 1]]))
  
  (fact "Results over time range of one month are grouped together by beginning of month"
    (let [d1  (clj-time/date-time 2013 1 1)
          d2  (clj-time/plus d1 (clj-time/days 5))
          end (clj-time/plus d2 (clj-time/months 2))]
      (create-test-users d1 1)
      (create-test-users d2 1)
      
      (reports/users-by-date
        (.getMillis d1)
        (.getMillis end)) => [[(.getMillis (reports/date-start-of-month d1)) 2]]))
  
  (fact "Results over time range of one year are grouped together by beginning of year"
    (let [d1  (clj-time/date-time 2013 1 1)
          d2  (clj-time/plus d1 (clj-time/weeks 3))
          end (clj-time/plus d1 (clj-time/years 2))]
      (create-test-users d1 1)
      (create-test-users d2 1)
      
      (reports/users-by-date
        (.getMillis d1)
        (.getMillis end)) => [[(.getMillis (reports/date-start-of-year d1)) 2]]))
  
  (fact "Cumulative user report contains an extra point after specified time range placed to current time"
    (let [d (pick-create (create-test-users (clj-time/date-time 2013 5 1) 2))]
      (reports/cumulative-users-by-date 0 d) => (just
                                                  (just [anything 2])
                                                  (just [#(> % d) 2]))))
  
  (fact "Cumulative user report provides an always growing list"
    (let [old-users-date (pick-create (create-test-users (clj-time/date-time 2013 5 1) 2))
          lone-wolf-date (pick-create (create-test-users (clj-time/date-time 2013 5 2) 1))
          mid-users-date (pick-create (create-test-users (clj-time/date-time 2013 5 4) 3))
          new-users-date (pick-create (create-test-users (clj-time/date-time 2013 5 5) 5))]
      
      (butlast (reports/cumulative-users-by-date old-users-date new-users-date)) => [[old-users-date 2] 
                                                                                     [lone-wolf-date 3] 
                                                                                     [mid-users-date 6] 
                                                                                     [new-users-date 11]]))
  
  (fact "Cumulative user report takes into account users created before the time limit in total sums"
    (let [old-users-date (pick-create (create-test-users (clj-time/date-time 2013 5 1) 2))
          lone-wolf-date (pick-create (create-test-users (clj-time/date-time 2013 5 2) 1))
          mid-users-date (pick-create (create-test-users (clj-time/date-time 2013 5 4) 3))
          new-users-date (pick-create (create-test-users (clj-time/date-time 2013 5 5) 5))]
      
      (last (butlast (reports/cumulative-users-by-date lone-wolf-date mid-users-date))) => [mid-users-date 6])))
