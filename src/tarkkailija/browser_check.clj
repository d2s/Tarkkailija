(ns tarkkailija.browser-check
  (:require [tarkkailija.env :as env]
            [sade.useragent :as ua]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.cookies :as cookies]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(def lowest-supported-browsers
  {:Chrome 7
   :Firefox 4
   :IE 9
   :Opera 11
   :Safari 5})

(defn load-old-browser-html []
  (slurp (clojure.java.io/resource "public/app/old-browser.html")))

(def cached-old-browser-html-load (memoize load-old-browser-html))

(defn old-browser-html [ua]
  (if (env/dev-mode?)
    (load-old-browser-html)
    (cached-old-browser-html-load)))

(defn resource-request? [request]
  (let [path-info (:path-info request)
        res ["png" "jpg" "gif" "html" "css" "js" "svg" "ttf"]]
    (if (.endsWith path-info "index.html")
      false ; special, index.html should trigger old browser handling
      (not (empty? (filter #(.endsWith path-info %) res))))))

(defn cookie-bypass? [request]
  (cookies/get "Tarkkailija-old-browser-check-bypass"))

(def browser-bypass-url "/api/bypass-browser-check")

(defpage browser-bypass-url []
  (noir.cookies/put! "Tarkkailija-old-browser-check-bypass" "true")
  (response/redirect "/"))

(defn old-browser-redirect-handler [handler]
  (fn [request]
    (if-let [ua (ua/parse-user-agent request)]
      (let [current-major (Integer/parseInt (if (-> ua :versionNumber :major s/blank? not)
                                              (-> ua :versionNumber :major)
                                              "10000"))
            lowest-supported-version (or (lowest-supported-browsers (-> ua :name keyword)) 0)
            old-browser? (and
                          (not (cookie-bypass? request))
                          (not (nil? (:path-info request)))
                          (not (.endsWith (:path-info request) browser-bypass-url))
                          (not (resource-request? request))
                          (< current-major lowest-supported-version))]
        (if old-browser?
          (response/content-type "text/html; charset=utf-8" (old-browser-html ua))
          (handler request)))
      (handler request))))
