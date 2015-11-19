(ns tarkkailija.reports
  (:use [sade.web])
  (:require [tarkkailija.users :as users]
            [tarkkailija.leiki :as leiki]
            [sade.i18n :as i18n]
            [clj-time.core :as clj-time])
  (:import [org.joda.time DateTime DateMidnight DateTimeZone]))

(def day-of-week #(.dayOfWeek %))
(def day-of-month #(.dayOfMonth %))
(def day-of-year #(.dayOfYear %))

(defmulti date-midnight-utc (fn [x] (type x)))
(defmethod date-midnight-utc org.joda.time.DateTime [date] (date-midnight-utc (.getMillis date)))
(defmethod date-midnight-utc java.lang.Long [millis]
  (.toDateTime (DateMidnight. millis DateTimeZone/UTC)))

(defn date-start-of [d shift-func]
  (clj-time/minus d (clj-time/days (- (-> d shift-func .get) 1))))

(defn date-start-of-week [millis]
  (let [cur (DateTime. millis DateTimeZone/UTC)]
    (-> cur
      (date-start-of day-of-week)
      date-midnight-utc)))

(defn date-start-of-month [millis]
  (let [cur (DateTime. millis DateTimeZone/UTC)]
    (-> cur
      (date-start-of day-of-month)
      date-midnight-utc)))

(defn date-start-of-year [millis]
  (let [cur (DateTime. millis DateTimeZone/UTC)]
    (-> cur
      (date-start-of day-of-year)
      date-midnight-utc))) 

(defn days-between-range [r]
  (if (= (- (:end r) (:start r)) java.lang.Long/MAX_VALUE)
    java.lang.Long/MAX_VALUE
    (clj-time/in-days (clj-time/interval (DateTime. (:start r)) (DateTime. (:end r))))))

(defn def-grouping-date [r]
  (let [days (days-between-range r)]
    (cond
      (<= days 31) date-midnight-utc
      (<= days 365) date-start-of-month
      :default date-start-of-year)))

(defn- group-with [d f]
  (group-by 
    (fn [e] (-> (:_created e) f .getMillis))
    d))

(defn- group-by-range [d r]
  (group-with d (def-grouping-date r)))

(defn convert-elements-to-vec [d]
  (map
    (fn [%] [(:_created %) (:users %)])
    d))

(defn- sum-values-to-vec [d]
  (for [[k v] d] [k (apply + (map :users v))]))

(defn- sort-by-time [d]
  (sort-by first d))

(defn- cumulate-values [d]
  (drop 1 (reductions (fn [a b] [(first b) (+ (second a) (second b))]) [0 0] d)))

(defn- to-long-seq [& args]
  (map #(java.lang.Long/parseLong %) (remove nil? args)))

(defn- add-to-all [d amount]
  (map (fn [e] [(first e) (+ amount (second e))]) d))

(defn- remove-nil-entries [d]
  (remove #(or (nil? %) (nil? (:_created %))) d))

(defn- duplicate-last-to-time [data t]
  (conj (vec data) [t (second (last data))]))

;; Report generation

(defn users-by-time []
  (convert-elements-to-vec (users/count-users-by-creation-time)))

(defn users-by-date 
  ([] (users-by-date 0 java.lang.Long/MAX_VALUE))
  ([start-time end-time]
    (-> 
      (users/count-users-by-creation-time start-time end-time)
      (group-by-range {:start start-time :end end-time})
      sum-values-to-vec
      sort-by-time)))

(defn cumulative-users-by-date
  ([] (cumulative-users-by-date 0 java.lang.Long/MAX_VALUE))
  ([start-time end-time]
    (let [c (users/count-users-to-date (- start-time 1))]
      (->
        (users/count-users-by-creation-time start-time end-time)
        (conj c)
        remove-nil-entries
        (group-by-range {:start start-time :end end-time})
        sum-values-to-vec
        sort-by-time
        cumulate-values
        (duplicate-last-to-time (java.lang.System/currentTimeMillis))))))

;; Leiki based reports

(defmulti popular-categories-weighted (fn [time1 time2 lang] [(type time1) (type time2)]))
  
(defmethod popular-categories-weighted [java.lang.Long java.lang.Long] 
  [startmillis endmillis lang] 
  (popular-categories-weighted (DateTime. startmillis) (DateTime. endmillis) lang))

(defmethod popular-categories-weighted [org.joda.time.DateTime org.joda.time.DateTime]
  [startdate enddate lang] 
  (filter (comp not empty? :name) (leiki/get-average-profile startdate enddate lang)))
  

;; REST API

(defjson "/api/reports/users/by-creation-date" {start :start end :end}
  (apply users-by-date (to-long-seq start end)))

(defjson "/api/reports/users/cumulative-by-creation-date" {start :start end :end}
  (apply cumulative-users-by-date (to-long-seq start end)))

(defjson "/api/reports/categories/popular-weighted" {start :start end :end}
  (apply popular-categories-weighted (conj (vec (to-long-seq start end)) (i18n/current-lang))))
