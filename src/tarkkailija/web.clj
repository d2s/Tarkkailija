(ns tarkkailija.web
  (:use [noir.core :only [defpage]]
        [sade.web]
        [sade.core]
        [tarkkailija.env]
        [tarkkailija.reports] ; include defpages
        [tarkkailija.notification] ; include defpages
        [monger.operators])
  (:require [noir.request :as request]
            [noir.server.handler :as handler]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [ring.util.codec :as codec]
            [sade.security :as security]
            [sade.mongo :as mongo]
            [sade.client :as client]
            [sade.status :refer [defstatus]]
            [sade.useragent :as ua]
            [sade.i18n :as i18n]
            [sade.email :as email]
            [tarkkailija.fixture :as fixture]
            [tarkkailija.leiki :as leiki]
            [tarkkailija.gis :as gis]
            [tarkkailija.rss :as rss]
            [tarkkailija.watch :as watch]
            [tarkkailija.users :as users]
            [tarkkailija.batchrun :as batch]
            [tarkkailija.browser-check :as browser-check]
            [tarkkailija.feedback :as feedback]
            [tarkkailija.html-loader :as html]
            [tarkkailija.config-reader :as config]))

;;
;; Web stuff
;;

(defpage "/" [] (redirect "/app/index.html"))
(defpage "/app/" [] (redirect "/app/index.html"))
(defpage "/app/index.html" [] (->> (html/load-index)
                                (no-cache-headers) ; we don't want index to be cached by browser - ever
                                (content-type "text/html; charset=utf-8")))

(defpage "/app/*.html" []
  (->> (html/load-html-file (:uri (request/ring-request)))
    (no-cache-headers) ; HTML files should *not* be cached by browser, angular caches them quite enough
    (content-type "text/html; charset=utf-8")))

(defpage "/app/css/main.css" []
  (->> (html/load-main-css)
    (content-type "text/css; charset=utf-8")))


;;
;; Config loading stuff
;;

(defpage "/app/js/config.js" []
  (let [d (->>
            (config/load-js-configs)
            (content-type "application/javascript; charset=utf-8"))]
    (if (dev-mode?)
      (->> d (no-cache-headers))
      d)))

;;
;; Error handling
;;

(defn handle-unknown-exception [request e]
  (let [ua (or (ua/parse-user-agent request) "Unresolved user-agent")]
    (log/error e "Unhandled exception occured, request user-agent:\n\n" ua "\n"))
  (status 500 (str "Unknown error occured: " (.getMessage e))))

(defn handle-known-exception [request e]
  (let [d (ex-data e)]
    (condp = (:type d)
      :not-found              (status 404 (.getMessage e))
      :not-authorized         (status 403 (.getMessage e))
      :authorization-required (status 401 (.getMessage e))
      :force-frontpage        (redirect "/")
      (handle-unknown-exception request e))))

(defn request-error-handler [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (if (not (nil? (ex-data e)))
          (handle-known-exception request e)
          (handle-unknown-exception request e))))))

(handler/add-custom-middleware browser-check/old-browser-redirect-handler)
(handler/add-custom-middleware request-error-handler)
(handler/add-custom-middleware i18n/lang-middleware)

;;
;; Helpers
;;

(defn merge-own-watch [w user-id]
  (merge w {:ownWatch (watch/is-watch-owner (:_id w) user-id)}))

(defn merge-subscribed-to-watch [w user-id]
  (merge w {:subscribed (if (watch/is-subscribed-for user-id w) true false)}))

;;
;; Rest stuff
;;

(defjson [:post "/api/users"] {json :json}
  (let [data (select-keys json [:email :password])]
    (if-let [user (security/create-user data)]
      (do
        (security/send-activation-mail-for {:user user
                                            :from "no-reply@etarkkailija.fi"
                                            :service-name "Tarkkailija"
                                            :host-url (client/uri)
                                            :lang (i18n/current-lang)})
        (ok :id (:_id user)))
      (fail :error.create_user))))

(defsecured [:post "/api/change-password"] {{:keys [currentpwd newpwd] :as json} :json}
  (if (security/login (:email (current-user)) currentpwd)
    (do
      (security/change-password (:email (current-user)) newpwd)
      (ok))
    (fail "error.invalid-current-password")))

(defpage [:post "/api/password-reset-email"] {json :json}
  (let [email (:email json)]
    (log/infof "Password reset for email '%s' requested" email)
    (if-let [user (users/find-user email)]
      (if-let [reset (security/send-password-change-mail-for  {:user user
                                                               :from "no-reply@etarkkailija.fi"
                                                               :service-name "Tarkkailija"
                                                               :host-url (client/uri)
                                                               :reset-link-prefix "/app/index.html#/password/change"})]
        (do
          (log/infof "Password reset email sent to '%s' with reset key '%s" email (:key reset))
          (status 200 "ok"))
        (status 500 "unknown error"))
      (status 404 "not found"))))


(defjson "/api/exists" {email :email}
  (security/user-exists? email))

(defpage "/api/show-watch/:watchid" {watchid :watchid range :range}
  (redirect (client/uri "/app/index.html#/search/" watchid "?range=" range)))

(defsecured "/api/watches-with-articles/:limit" {limit :limit}
  (let [watches  (watch/find-multiple-watches-for-user (:_id (current-user)) (Integer/parseInt limit))
        articles (pmap leiki/find-similar-content-tarkkailija watches)
        embedded (map (fn [w f] {:watch w :articles (:articles f) :totalresults (:totalresults f)}) watches articles)]
    (ok :watches embedded)))

(defjson "/api/public/watches/limit/:limit" {limit :limit}
  (let [watches  (watch/find-public-watches (Integer/parseInt limit) :clear-secure true)
        articles (pmap leiki/find-similar-content-tarkkailija watches)
        embedded (map (fn [w f] {:watch w :articles (:articles f) :totalresults (:totalresults f)}) watches articles)]
    (ok :watches embedded)))

(defjson [:get "/api/public/watches"] {limit :limit skip :skip query :query}
  (let [watches (watch/find-public-watches
                 (Integer/parseInt limit)
                 (Integer/parseInt skip)
                 query
                 (:_id (current-user))
                 {:enrich true})]
    (ok
      :watches   (map (comp watch/clear-secure-data #(merge-subscribed-to-watch % (:_id (current-user)))) watches)
      :total     (watch/count-public-watches)
      :queryHits (watch/count-public-watches query))))

(defjson [:get "/api/watches/:id"] {id :id}
  (let [watch (watch/find-watch id :enrich true :rss-token true)
        userid (:_id (current-user))]
    (cond
      (or (:publicFeed watch) (= userid (:userid watch))) (ok :watch
                                                            (-> watch
                                                              (merge-subscribed-to-watch userid)
                                                              (merge-own-watch  userid)
                                                              (watch/clear-secure-data)))
      (and (not (:publicFeed watch)) (nil? userid))       (throw (ex-info "Authorization required" {:type :authorization-required}))
      :else                                               (throw (ex-info (str "No such watch id=" id) {:type :not-found})))))

(defjson "/api/watches" []
  (let [watches (watch/find-all-watches-for-user (:_id (current-user)) :enrich true)]
    (ok :watches (map (comp watch/clear-secure-data #(merge-subscribed-to-watch % (:_id (current-user)))) watches))))

(defsecured [:post "/api/watches"] {{:keys [areas categories geoJson] :as json} :json}
  (let [userid  (:_id (current-user))
        input   (select-keys json [:name :email :areas :categories :emailChecked :publicFeed :geoJson])
        data    (assoc input :userid userid :subscribeEmails {})]
    (if-let [watch (watch/create-watch userid input :clear-secure true)]
      (ok :watch (merge-own-watch watch userid))
      (fail :error.create_watch))))

(defsecured [:put "/api/watches"] {{:keys [id areas categories geoJson] :as json} :json}
  (let [userid (:_id (current-user))
        input  (select-keys json [:name :email :areas :categories :emailChecked :publicFeed :geoJson])]
    (watch/check-own-watch id userid)
    (if-let [watch (watch/update-watch id input :clear-secure true)]
      (ok :watch (merge-own-watch watch userid))
      (fail :error.update_watch))))

(defsecured [:delete "/api/watches/:id"] {id :id}
  (let [userid (:_id (current-user))]
    (watch/check-own-watch id userid)
    (if-let [response (watch/delete-watch id)]
      (ok :response response)
      (fail :error.delete_watch))))

(defpage [:get "/api/watches/:id/rss"] {id :id token :token}
  (if-let [watch (watch/find-watch id)]
    (content-type "application/rss+xml; charset=utf-8" (rss/get-rss-for-watch id token))
    (status 404 "Not found")))

(defsecured [:get "/api/subscribes"] []
  (map #(dissoc (merge % {:id (:_id %)}) :_id) (watch/get-subscriptions-for-user (:_id (current-user)))))

(defsecured [:put "/api/subscribes"] {{:keys [watchId email lang] :as json} :json}
  (if (watch/add-subscriber watchId (current-user) email lang)
    {:ok true}
    (fail :error.update_watch_subscription)))

(defsecured [:delete "/api/subscribes/:watchId"] {watchId :watchId}
  (if (watch/remove-subscriber watchId (:_id (current-user)))
    {:ok true}
    (fail :error.delete_watch_subscription)))

; TODO: this url is ugly, but the reason is in enlive (see tarkkailija.batchrun)
(defpage [:get "/api/subscribes/remove-with-token/:watchId/:email/:token"] {watchId :watchId username :email token :token}
  (log/debugf "Removing watch subscription with token '%s'" token)
  (let [user (users/find-user username)
        subscribe-email (or
                          (->> (watch/get-subscriptions-for-user (:_id user))
                            (filter #(= watchId (str (:_id %))))
                            first
                            :email)
                          (:email user))]
    (redirect
      (str "/app/index.html#/unsubscribe?watchId=" watchId
        "&username=" (codec/url-encode username)
        "&email=" (codec/url-encode subscribe-email)
        "&token=" (codec/url-encode token)))))

(defjson [:delete "/api/subscribes/remove-with-token"] {:keys [watchId username email token]}
  (let [user (users/find-user username)
        calculated-hash (security/create-hash-for ((comp :salt :private) user) username watchId)]
    (if (= token calculated-hash)
      (do
        (watch/remove-subscriber watchId (:_id user))
        (log/infof "Removed subscription for user '%s' to address '%s' from watch '%s'" username email watchId)
        (ok))
      (status 403 "Forbidden"))))

(defn week-ago-millis []
  (.getMillis (time/minus (org.joda.time.DateTime. (System/currentTimeMillis)) (time/weeks 1))))

(defjson [:any "/api/feed"] {{:keys [areas categories geojson startdatemillis enddatemillis limit offset]
                              :or {areas "" categories [] geojson nil startdatemillis (week-ago-millis) enddatemillis (System/currentTimeMillis) limit 40 offset 0}} :json}
  (let [email        (:email (current-user))
        articles     (leiki/find-similar-content-tarkkailija {:startdate (org.joda.time.DateTime. (Long/parseLong (str startdatemillis)))
                                                              :enddate   (org.joda.time.DateTime. (Long/parseLong (str enddatemillis)))
                                                              :areas areas
                                                              :categories categories
                                                              :geojson geojson
                                                              :maxtextlength 280
                                                              :limit limit
                                                              :offset offset})]
   (assoc articles :ok true)))


(defpage "/api/map" [] (gis/raster-images (request/ring-request)))
(defpage "/api/map-image/:articleId" {articleId :articleId} (gis/article-map-image articleId))

(defjson "/api/categories" {term :term}
  (if (nil? term)
    (leiki/frontpage-categories)
    (leiki/category-search term (i18n/current-lang))))

(defjson "/api/areas-with-term" {term :term}
  (leiki/area-search-with-text term (i18n/current-lang)))

(defjson "/api/areas-with-geojson" {geojson :geojson}
  (leiki/area-search-with-geojson geojson (i18n/current-lang)))

(defjson "/api/sources" []
  (leiki/get-feed-sources))

(defjson "/api/exactgeojson" {areas :areas}
  (leiki/exact-geojson-fetch areas))

(defjson [:post "/api/feedback"] {{:keys [text replyAddress feedbackType] :as json} :json}
  (let [requ (request/ring-request)
        uuid (str (java.util.UUID/randomUUID))
        feedback {:uuid uuid
                  :type feedbackType
                  :text text
                  :reply-to replyAddress
                  :remote-addr (:remote-addr requ)
                  :user-agent (get-in requ [:headers "user-agent"])
                  :timestamp (. (new org.joda.time.DateTime) toString "dd.MM.yyyy HH:mm:ss")}]

    (let [feedback-with-userid (if-let [userid (:_id (current-user))] (merge feedback {:userid userid}) feedback)
          email-send-result (feedback/send-feedback-mail-for {:from (str "Feedback, log-ID: " uuid)
                                                              :service-name "Tarkkailija"
                                                              :host-url (client/uri)
                                                              :feedback feedback-with-userid})]
      (if (= (:result email-send-result) :sent)
        (do
          (log/infof "Feedback sent, id = %s" (:uuid feedback))
          {:ok true})
        (do
          (log/error "Email send error occured, error message: \n" (:reason email-send-result) "\n")
          (fail :error.send_feedback))))))

(defpage "/api/show-article" {:keys [id]}
  (redirect (leiki/leiki-widget-uri id)))

(defjson "/api/articles/:articleId" {articleId :articleId}
  (leiki/get-article-data articleId))

(defjson [:post "/api/articles/similar"] {{:keys [articleId startdatemillis enddatemillis limit offset]
                                           :or {startdatemillis (week-ago-millis) enddatemillis (System/currentTimeMillis) limit 40 offset 0}} :json}
  (leiki/find-similar-content {:article-id articleId
                               :startdate (org.joda.time.DateTime. (Long/parseLong (str startdatemillis)))
                               :enddate   (org.joda.time.DateTime. (Long/parseLong (str enddatemillis)))
                               :sort "date"
                               :maxtextlength 200
                               :limit limit
                               :offset offset}))

;;
;; Status
;;

(defstatus :time  (. (new org.joda.time.DateTime) toString "dd.MM.yyyy HH:mm:ss"))
(defstatus :mode  (mode))
(defstatus :build-info build-info)

;;
;; Dev stuff
;;

(when (dev-mode?)

  (defjson [:post "/dev/fixture/minimal"] []
    (fixture/apply!)
    (ok :ok "didit"))

  (defjson "/dev/api/users" []
    (let [users (map security/non-private (mongo/find-many :users))]
      (ok :users users)))

  (defjson "/dev/api/fixture/activation-url/:email" {email :email}
    (if-let [user (security/get-user-by-email email)]
      (let [key (security/get-activation-key (:_id user))]
        (ok :activation-url (client/uri "/security/activate/" (:activation-key key))))
      (fail :error.key)))

  (defjson "/dev/api/fixture/change-password-url/:email" {email :email}
    (if-let [user (security/get-user-by-email email)]
      (let [key (:reset-key (security/get-change-password-key (:_id user)))]
        (ok :change-password-url (client/uri "/app/index.html#/password/change/" key)))
      (fail :error.key)))

  (defjson "/dev/api/fixture/force-watch-mail-sending" []
    (batch/send-emails-for-all-watches))

  (defjson "/dev/api/fixture/clear-db" [] (mongo/clear!))

  (defjson "/dev/api/fixture/create-activated-user" {:keys [username password firstname lastname apikey] :or {firstname "Galle" lastname "Generated"}}
    (when-not (security/user-exists? username)
      (if (nil? apikey)
        (security/non-private (security/create-user {:email username :password password :firstName firstname :lastName lastname :enabled true}))
        (security/non-private (security/create-user-with-apikey {:email username :password password :firstName firstname :lastName lastname :enabled true :apikey apikey})))))

  (defjson "/dev/api/email-sending/disable" []
    (email/disable-sending!)
    (ok :ok true))

  (defjson "/dev/api/email-sending/enable" []
    (email/enable-sending!)
    (ok :ok true))

  (def characters (map char (concat (range 48 58) (range 66 91) (range 97 123))))
  (defn random-char []
    (nth characters (rand-int (- (count characters) 1))))
  (defn random-string [length]
    (apply str (take length (repeatedly random-char))))
  (defn rand-email []
    (str (random-string (+ 3 (rand-int 5))) "@" (random-string (+ 3 (rand-int 5))) ".fi"))

  (def areas (lazy-seq (flatten (pmap #(leiki/area-search-with-text % "fi") ["tampere" "helsinki" "vuosaari" "ypäjä" "jokioinen" "inari" "kuopio"]))))

  (defjson "/dev/api/fixture/users/create-random/:amount" {amount :amount}
    (dotimes [_ (Integer/parseInt amount)]
      (security/create-user
        {:email (rand-email)
         :password (random-string 10)
         :enabled true})))

  (defjson "/dev/api/fixture/watches/create-random/:amount" {amount :amount type :type}
    (let [users (users/find-all-users)
          publicWatches (if (not (nil? type)) (= (keyword type) :public) nil)]
      (no-cache-headers
        (map
          (fn [_]
            (watch/create-watch
              (:_id (nth users (rand-int (- (count users) 1))))
              {:name (random-string 10)
               :email (rand-email)
               :areas (map :value (take (+ 1 (rand-int 2)) areas))
               :categories (map :id (take (+ 1 (rand-int 3)) (shuffle (leiki/frontpage-categories))))
               :publicFeed (if (nil? publicWatches) (= 1 (rand-int 2)) publicWatches)
               :geoJson nil}))
            (range 0 (Integer/parseInt amount)))))))
