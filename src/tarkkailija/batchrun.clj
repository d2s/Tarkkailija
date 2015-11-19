(ns tarkkailija.batchrun
  (:require [tarkkailija.watch :as watch]
            [tarkkailija.leiki :as leiki]
            [tarkkailija.users :as users]
            [tarkkailija.env :as env]
            [sade.cron :as cron]
            [sade.client :as client]
            [sade.email :as email]
            [sade.i18n :as i18n]
            [sade.security :as security]
            [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as enlive]
            [ring.util.codec :as codec]
            [clj-time.format :as date-format]))

; default pattern is on monday noon
(def ^:dynamic ^:String *watch-email-send-interval-pattern* (env/property "watch.cron" "0 12 * * 1"))
(def ^:private formatter (date-format/formatter "yyyy-MM-dd'T'HH:mm:ss"))
(def ^:private max-tries 5)
(def ^:private wait-interval-between-tries 10000)

(defn set-email-send-pattern [^:String pattern]
  (if (cron/valid-pattern pattern)
    (alter-var-root #'*watch-email-send-interval-pattern* (constantly pattern))
    (throw (RuntimeException. (str "Invalid cron pattern for batchrun provided, pattern: " pattern)))))

(defn- convert-date-str [s]
  (.toString (date-format/parse formatter s) "dd.MM.yyyy"))

(defn- create-unsubscribe-link [watch user]
  ; TODO: reason for this url is in the fact that for some reason enlive forcefully encodes strings
  ;       passed into set-attr. This screws up for example & character and breaks the normal get parameters
  (client/uri
    "/api/subscribes/remove-with-token"
    "/" (:_id watch)
    "/" (:email user)
    "/" (codec/url-encode (security/create-hash-for ((comp :salt :private) user) (:email user) (:_id watch)))))

(defn populate-article-snippets [articles]
  (enlive/clone-for [a articles]
    [:.article-date]                    (enlive/content (convert-date-str (:date a)))
    [:.article-title :.article-link]    (enlive/do->
                                          (enlive/set-attr :href (:link a))
                                          (enlive/content (:headline a)))
    [:.article-source]                 (enlive/content (:sourceName a))
    [:.article-content :.article-link] (enlive/do->
                                         (enlive/content (:description a))
                                         (enlive/set-attr :href (:link a)))))

(enlive/deftemplate
  watch-email-template
  (enlive/html-resource (clojure.java.io/resource "private/email-templates/watch-email.html"))
  [watch articles total-count user lang]
  [:head :base]     (enlive/set-attr :href (client/uri))
  [:#watch-name]    (enlive/content (:name watch))
  [:#article-count] (enlive/content total-count)
  [:#watch-link]    (enlive/set-attr :href (str "/api/show-watch/" (:_id watch) "?range=week"))
  [:#unsubscribe-link] (enlive/set-attr :href (create-unsubscribe-link watch user))
  [:#articles :table] (populate-article-snippets articles)
  [:*] (i18n/localize-enlive-content lang))

(defn build-emails-for-watch [watch-id]
  (let [watch           (watch/find-watch watch-id)
        subscriptions   (watch/get-email-subscriptions-of-watch watch-id)
        articles        (leiki/find-similar-content-tarkkailija watch)]
    (when-not (empty? (:articles articles))
      (for [s subscriptions]
        {:address (:email s)
         :watch watch
         :subject (str "Tarkkailija - " (i18n/with-lang (:lang s) (i18n/loc "email.watch.weekly.subject")) " " (:name watch))
         :content (apply str (watch-email-template watch (:articles articles) (:totalresults articles) (users/find-user-by-id (:userId s)) (:lang s)))}))))

(defn build-emails-for-watch-with-retries
  ([watch-id] (build-emails-for-watch-with-retries watch-id 1))
  ([watch-id times-tried]
    (try
      (build-emails-for-watch watch-id)
      (catch Throwable t
        (when (> times-tried max-tries) (throw t))
        (log/warnf "Failed building email for watch='%s', retrying (attempt %d/%d) in %d ms"
                   watch-id times-tried max-tries wait-interval-between-tries)
        (Thread/sleep wait-interval-between-tries)
        (build-emails-for-watch-with-retries watch-id (inc times-tried))))))

(defn send-watch-emails-for-watch [watch-id]
  (log/info "Building HTML email for watch" watch-id "- retrieving data from Leiki Focus")
  (if-let [mails (build-emails-for-watch-with-retries watch-id)]
    (do
      (log/infof "Sending emails for watch '%s' - %d recipients in total" watch-id (count mails))
      (doseq [m mails]
        (log/debugf "Sending watch email (id=%s) to address %s" watch-id (:address m))
        (email/send-mail
          (:address m)
          "vahti@etarkkailija.fi"
          (:subject m)
          #_(str "Tarkkailija - artikkelit vahdille " ((comp :name :watch) m))
          (:content m)))
      (log/infof "All emails sent for watch '%s'" watch-id))
    (log/infof "No new articles for watch '%s', no emails to send" watch-id)))

(defn send-emails-for-all-watches []
  (let [watch-ids (watch/find-all-watch-ids)]
    (log/info "Watch email send trigger, processing" (count watch-ids) "watches")
    (doseq [_ (pmap (fn [id]
                      (try
                        (send-watch-emails-for-watch id)
                        (catch Exception e
                          (log/warn e "Couldn't send watch emails for watch id=" id))))
                    watch-ids)])))

(defn start-watch-email-scheduler []
  (log/info "Starting watch email scheduler with pattern" *watch-email-send-interval-pattern*)
  (cron/repeatedly-schedule *watch-email-send-interval-pattern* send-emails-for-all-watches))
