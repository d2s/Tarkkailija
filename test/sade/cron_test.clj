(ns sade.cron-test
  (:use midje.sweet)
  (:require [sade.cron :as cron]
            [clj-time.core :as clj-time]))

(facts "cron"

  (let [now            (org.joda.time.LocalDateTime. (System/currentTimeMillis))
        monday-morning (clj-time/local-date-time 2013 04 01 9)
        basetime       (clj-time/local-date-time 2013 02 22 9 22 0 0)
        end-of-year    (clj-time/local-date-time 2013 12 31 23 59 59)]

    (fact "cron syntax is parsed to map correctly"
      (cron/format-to-map "30 6 22 * *") => {:day-of-week "*", :month "*", :day-of-month 22, :hour 6, :min 30})

    (fact "next triggering interval is parsed correctly"
      (cron/next-interval "* * * * *" :basetime basetime) => (clj-time/plus basetime (clj-time/minutes 1))
      (cron/next-interval "30 6 * * *" :basetime basetime) => (clj-time/local-date-time 2013 02 23 6 30)
      (cron/next-interval "30 6 1 * *" :basetime basetime) => (clj-time/local-date-time 2013 03 1 6 30)
      (cron/next-interval "30 6 1 6 *" :basetime basetime) => (clj-time/local-date-time 2013 06 1 6 30)
      (cron/next-interval "0 0 * * 0" :basetime basetime) => (clj-time/local-date-time 2013 02 24)
      (cron/next-interval "0 0 * * 3" :basetime basetime) => (clj-time/local-date-time 2013 02 27)
      (cron/next-interval "0 12 * * 1" :basetime basetime) => (clj-time/local-date-time 2013 02 25 12)
      (cron/next-interval "0 12 * * 1" :basetime (clj-time/local-date-time 2013 04 29 12)) => (clj-time/local-date-time 2013 05 06 12)
      (cron/next-interval "0 12 * * 1" :basetime monday-morning) => (clj-time/plus monday-morning (clj-time/hours 3))
      (cron/next-interval "@daily" :basetime basetime) => (clj-time/local-date-time 2013 02 23)
      (cron/next-interval "@yearly" :basetime basetime) => (clj-time/local-date-time 2014 01 01)
      (cron/next-interval "@monthly" :basetime basetime) => (clj-time/local-date-time 2013 03 01)
      (cron/next-interval "@weekly" :basetime basetime) => (clj-time/local-date-time 2013 02 24)
      (cron/next-interval "@hourly" :basetime basetime) => (clj-time/local-date-time 2013 02 22 10)
      (cron/next-interval "@daily" :basetime end-of-year) => (clj-time/local-date-time 2014 01 01))))
