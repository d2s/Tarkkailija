(ns tarkkailija.gis
  (:use [clojure.tools.logging])
  (:require [clj-http.client :as http]
            [sade.status :refer [defstatus]]
            [cheshire.core :as json]
            [clojure.walk :as walk]
            [tarkkailija.env :as env]))

(def ^:dynamic ^String *mml-map-host*      (env/property "gis.mapserver.url" "https://karttakuva.maanmittauslaitos.fi/maasto/wmts"))
(def ^:dynamic ^String *gis-minimap-host*  (env/property "gis.minimap.url" "localhost"))
(def ^:dynamic ^String *mml-map-username*  (env/property "wmts.raster.username" nil))
(def ^:dynamic ^String *mml-map-password*  (env/property "wmts.raster.password" nil))

(defn set-map-servers! [{:keys [map-server minimap-server]}]
  (when (not (nil? map-server))
    (info "Setting map server url to " map-server)
    (alter-var-root #'*mml-map-host* (constantly map-server)))
  (when (not (nil? minimap-server))
    (info "Setting minimap server url to " minimap-server)
    (alter-var-root #'*gis-minimap-host* (constantly minimap-server))))

(defn set-map-server-authentication! [{:keys [mml-username mml-password]}]
  (when (not (nil? mml-username))
    (info "Setting username for MML map api")
    (alter-var-root #'*mml-map-username* (constantly mml-username)))
  (when (not (nil? mml-password))
    (info "Setting password for MML map api")
    (alter-var-root #'*mml-map-password* (constantly mml-password))))

(defn raster-images [request]
  (http/get *mml-map-host* {:query-params (:query-params request)
                            :headers {"accept-encoding" (get-in request [:headers "accept-encoding"])}
                            :basic-auth [*mml-map-username* *mml-map-password*]
                            :as :stream}))

(defn article-map-image [article-id]
  (http/get (str *gis-minimap-host* "/tarkkailija/MapGenerator?articleId=")
    {:query-params {:articleId article-id}
     :as :stream}))

;;
;; Status
;;

(defstatus :sito
  (let [resp (http/get (str *gis-minimap-host* "/tarkkailija/Status"))
        body (walk/keywordize-keys (json/parse-string (:body resp)))]
    (if (= false (:alive body)) (throw (IllegalStateException. (:message body))))
    true))