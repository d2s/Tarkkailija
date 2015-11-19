(ns tarkkailija.rss
  (:use sade.rss
        [clojure.string :only [blank?]])
  (:require [tarkkailija.watch :as watch]
            [tarkkailija.leiki :as leiki]
            [sade.client :as client]
            [cheshire.core :as json]
            [clj-time.core :as clj-time]))

(defn get-item [article]
  (let [{:keys [headline description link date locationx locationy]} article
        item {:title       headline
              :description description
              :link        link
              "georss:point" (str locationx " " locationy)
              :pubDate     date
              :guid        link}]
    (into {} (remove (comp blank? val) item))))

(defn get-rss-url [watch-id]
  (str (client/server-address) "/api/watches/" watch-id "/rss"))

(defn- as-string [list]
  (apply str (interpose ", " list)))

(defn- get-type-tag [type]
  (str "georss:" type))

(defn get-channel [watch]
  (let [item {:title       (str "Tarkkailija: " (:name watch))
              :link        (get-rss-url (:_id watch))
              :ttl         (-> (clj-time/days 3) .toStandardMinutes .getMinutes str)
              :description (str "Alueet: " (as-string (map :name (:areas watch))) "; Aihealueet: " (as-string (map :name (:categories watch))))}]
    (if-not (blank? (:geoJson watch))
      (let [geo (-> watch :geoJson json/parse-string)]
        (assoc item (-> geo (get "type") get-type-tag) (map #(str (Double/toString %) " ") (-> geo (get "coordinates") flatten))))
      item)))

(defn get-rss-feed [watch articles]
  (generate-rss (get-channel watch) (map get-item articles)))

(defn authorized? [watch token]
  (or (:publicFeed watch) (= token (watch/calculate-auth-token watch))))

(defn get-rss-for-watch [watch-id token]
  (let [watch  (watch/find-watch watch-id :enrich true)]
    (if (authorized? watch token)
      (get-rss-feed watch (:articles (leiki/find-similar-content-tarkkailija {:areas      (map :id (:areas watch))
                                                                              :categories (map :id (:categories watch))
                                                                              :geojson    (:geoJson watch)})))
      (throw (ex-info "Not authorized" {:type :not-authorized})))))
