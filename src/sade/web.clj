(ns sade.web
  (:use [noir.core :only [defpage]]
        [clojure.walk :only [keywordize-keys]]
        [sade.core]
        [sade.status]
        [clojure.tools.logging])
  (:require [noir.request :as request]
            [noir.response :as resp]
            [noir.session :as session]
            [noir.server :as server]
            [sade.security :as security]
            [sade.mongo :as mongo]
            [cheshire.core :as json]))

;;
;; Noir handler wrappers
;;

(defn no-cache-headers [content]
  (resp/set-headers {"Cache-Control" "no-cache, no-store, must-revalidate"
                     "Pragma" "no-cache"
                     "Expires" "Thu, 1 Jan 1970 00:00:00 GMT"}
                    content))

(defn uncached-resp [content status]
  (->> content
    (no-cache-headers)
    (resp/status status)))

(defn status [status content]
  (uncached-resp content status))

(defn redirect [url]
  (no-cache-headers (resp/redirect url)))

(defn content-type [ctype content]
  (no-cache-headers (resp/content-type ctype content)))

;;
;; JSON
;;

(defmacro defjson [path params & content]
  `(defpage ~path ~params
     (let [r# (do ~@content)
           status# (if (or (-> r# :ok nil?) (:ok r#)) 200 403)]
       (uncached-resp (resp/json r#) status#))))

(defn from-json []
  (get-in (request/ring-request) [:params :json]))

(defn from-query []
  (keywordize-keys (:query-params (request/ring-request))))

(defn json-body [handler]
  (fn [request]
    (handler
      (if (.startsWith (get-in request [:headers "content-type"] "") "application/json")
        (update-in request [:params] assoc :json (json/parse-string (slurp (:body request)) true))
        request))))

(server/add-middleware json-body)

;;
;; Request parsing
;;

(defn host [request]
  (str (name (:scheme request)) "://" (get-in request [:headers "host"])))

(defn user-agent [request]
  (str (get-in request [:headers "user-agent"])))

(defn client-ip [request]
  (or (get-in request [:headers "real-ip"]) (get-in request [:remote-addr])))

;;
;; User Sessions
;;

(defn current-user
  "fetches the current user from 1) http-session 2) apikey from headers"
  [] (or (session/get :user) ((request/ring-request) :user)))

(defn logged-in? []
  (not (nil? (current-user))))

(defjson "/security/user" []
  (if-let [user (current-user)]
    (ok :user user)
    (fail :not-logged-in)))

(defmacro defsecured [path params & content]
  `(defpage ~path ~params
     (if (logged-in?)
       (let [r# (do ~@content)
             status# (if (or (-> r# :ok nil?) (:ok r#)) 200 403)]
         (uncached-resp (resp/json r#) status#))
       (uncached-resp "unauthorized" 401))))

;;
;; Login/logout:
;;

(defjson [:any "/security/login"] {:as form}
  (let [json     (from-json)
        data     (merge form json)
        username (:username data)
        password (:password data)]
    (if-let [user (security/login username password)]
      (do
        (info "login successful:" username)
        (session/put! :user user)
        (ok :user user))
      (do
        (info "login failed:" username)
        (fail :error.login)))))

(defpage "/security/logout" []
  (session/clear!)
  (redirect "/"))

(defpage "/security/activate/:activation-key" {key :activation-key}
  (if-let [user (security/activate-account key)]
    (do
      (infof "User account '%s' activated, auto-logging in the user" user)
      (session/put! :user user)
      (redirect "/"))
    (do
      (warnf "Invalid user account activation attempt with key '%s', possible hacking attempt?" key)
      (redirect "/"))))

(defpage [:post "/security/reset-password/:reset-key"] {key :reset-key json :json}
  (let [pwd (:password json)]
    (infof "Password reset with reset key '%s' requested" key)
    (if-let [user (security/change-password-via-reset-key key pwd)]
      (do
        (infof "User account '%s' password reset success for key '%s'" (:email user) key)
        (uncached-resp "ok" 200))
      (do
        (warnf "Invalid user password reset request with key '%s', possible hacking attempt?" key)
        (uncached-resp "not found" 404)))))

;;
;; Apikey-authentication
;;

(defn- parse [required-key header-value]
  (when (and required-key header-value)
    (if-let [[_ k v] (re-find #"(\w+)\s*[ :=]\s*(\w+)" header-value)]
      (if (= k required-key) v))))

(defn apikey-authentication
  "Reads apikey from 'Auhtorization' headers, pushed it to :user request header
   'curl -H \"Authorization: apikey APIKEY\" http://localhost:8000/api/application"
  [handler]
  (fn [request]
    (let [authorization (get-in request [:headers "authorization"])
          apikey        (parse "apikey" authorization)]
      (handler (assoc request :user (security/login-with-apikey apikey))))))

(server/add-middleware apikey-authentication)

;;
;; Ping & Status
;;

(defjson "/system/ping" [] {:ok true})

(defpage "/system/status" []
  (uncached-resp (resp/json (server-status)) 200))

