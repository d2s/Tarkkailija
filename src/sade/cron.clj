(ns sade.cron
  (:require [clj-time.core :as clj-time]))

(def ^:private clj-time-mapping
  {:month clj-time/month
   :day-of-month clj-time/day
   :hour clj-time/hour
   :min clj-time/minute
   :day-of-week clj-time/day-of-week})

(def ^:private key-order [:month :day-of-month :day-of-week :hour :min])

(defn- restricted? [& r]
  (= (count (filter true? (map #(not= % "*") r))) (count r)))

(defn format-to-map [s]
  (let [parts   (clojure.string/split s #" ")
        convert (fn [s] (if (re-matches #"-?[0-9]+" s) (Integer. (re-find  #"\d+" s )) s))]
     {:day-of-week  (convert (nth parts 4))
      :month        (convert (nth parts 3))
      :day-of-month (convert (nth parts 2))
      :hour         (convert (nth parts 1))
      :min          (convert (nth parts 0))}))

(defn find-most-significant [r]
 (if-let [result
   (first
     (filter #(not (= "*" (second %)))
             (sort
               (fn [a b] (< (.indexOf key-order (first a)) (.indexOf key-order (first b))))
               (for [[k v] r] [k v]))))]
   result
   [:all-stars 0]))

(defn increment-by-most-significant-part [t r]
  (let [significant (find-most-significant r)
        days-till-next-week #(clj-time/plus % (clj-time/days (- 8 (clj-time/day-of-week %))))
        days-till-week-day  #(clj-time/plus % (clj-time/days (- (second significant) (clj-time/day-of-week %))))]
    (((first significant) {:month       #(clj-time/plus % (clj-time/years 1))
                           :day-of-month #(clj-time/plus % (clj-time/months 1))
                           :hour         #(clj-time/plus % (clj-time/days 1))
                           :min          #(clj-time/plus % (clj-time/hours 1))
                           :day-of-week  (if (>= (clj-time/day-of-week t) (second significant))
                                            (comp days-till-week-day days-till-next-week)
                                            days-till-week-day)
                           :all-stars    #(clj-time/plus % (clj-time/minutes 1))}) t)))

(defn parse-predefined [pattern]
  (get {"@annually" "0 0 1 1 *"
        "@yearly"   "0 0 1 1 *"
        "@monthly"  "0 0 1 * *"
        "@weekly"   "0 0 * * 0"
        "@daily"    "0 0 * * *"
        "@hourly"   "0 * * * *"}
       pattern))

(defn next-interval [pattern & {:keys [basetime] :or {basetime (org.joda.time.LocalDateTime. (System/currentTimeMillis))}}]
  (if-let [predefined (parse-predefined pattern)]
    (next-interval predefined :basetime basetime)
    (let [cron-rule (format-to-map pattern)
          convert   (fn [rule f] (if (= rule "*") f (fn [& _] rule)))
          initial   (clj-time/local-date-time
                      (clj-time/year basetime)
                      ((convert (:month cron-rule) (:month clj-time-mapping)) basetime)
                      ((convert (:day-of-month cron-rule) (:day-of-month clj-time-mapping)) basetime)
                      ((convert (:hour cron-rule) (:hour clj-time-mapping)) basetime)
                      ((convert (:min cron-rule) (:min clj-time-mapping)) basetime)
                      0)]

      (if (clj-time/after? initial basetime)
        ; FIXME: Hack, bypasses the problem with patterns such as 0 12 * * 1 and basetime for example at monday 9am
        (if (and (restricted? (:day-of-week cron-rule)) (not= (clj-time/day-of-week initial) (:day-of-week cron-rule)))
          (increment-by-most-significant-part initial cron-rule)
          initial)
        (increment-by-most-significant-part initial cron-rule)))))

(defn valid-pattern [pattern]
  (try
    (next-interval pattern)
    true
    (catch Throwable t
      false)))

(defn millis-between [t1 t2]
  (.toDurationMillis (clj-time/interval (.toDateTime t1) (.toDateTime t2))))

(defn schedule-once [pattern f & {:keys [basetime] :or {basetime (org.joda.time.LocalDateTime. (System/currentTimeMillis))}}]
  (future
    (Thread/sleep (millis-between basetime (next-interval pattern :basetime basetime)))
    (f)))

(defn repeatedly-schedule [pattern f & {:keys [times] :or {times nil}}]
  (future
    (let [calls (repeatedly #(schedule-once pattern f))]
      (if (nil? times)
        (doseq [x calls] @x)
        (doseq [x (take times calls)] @x)))))
