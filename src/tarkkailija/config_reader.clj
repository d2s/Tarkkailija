(ns tarkkailija.config-reader
  (:require [clojure.string :as s]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [sade.mongo :as mongo]
            [sade.email :as email]
            [sade.i18n :as i18n]
            [tarkkailija.env :as env]
            [tarkkailija.batchrun :as batchrun]
            [tarkkailija.feedback :as feedback]
            [tarkkailija.leiki :as leiki]
            [tarkkailija.gis :as gis]
            [tarkkailija.notification :as notification]))

(def ^:dynamic ^String *props* nil)

(def ^:private overlayable-properties
   #{:db.url
     :email.user
     :email.password
     :email.ssl
     :email.host
     :feedback.target.email
     :watch.cron
     :leiki.url
     :gis.mapserver.url
     :gis.minimap.url
     :wmts.raster.username
     :wmts.raster.password
     :migration.dir
     :tarkkailija.ga.key})

(defn load-props [file-name]
  (with-open [^java.io.Reader reader (clojure.java.io/reader (clojure.java.io/resource file-name))]
    (let [props (java.util.Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(keyword k) (read-string v)])))))

(defn set-prop-via [f v]
  (when (not (nil? v)) (f (s/trim v))))

(defn sysproperties-as-map []
  (into {} (for [[k v] (System/getProperties)] [(keyword k) v])))

(defn overlay-with-sysprops [props]
  (merge props (filter
                 #(and (overlayable-properties (key %)) (-> % val s/blank? not) )
                 (sysproperties-as-map))))

(defn init-configs [^:String mode]
  (let [path  (str "private/configs/" (name mode) ".properties")
        props (overlay-with-sysprops (load-props path))]

    (alter-var-root #'*props* (constantly props))

    (log/info "Initializing configurations from" path)
    (set-prop-via mongo/set-mongo-uri! (:db.url props))
    (email/set-smtp-settings! {:user (:email.user props)
                               :pass (:email.password props)
                               :ssl  (:email.ssl props)
                               :host (:email.host props)})
    (set-prop-via batchrun/set-email-send-pattern (:watch.cron props))
    (set-prop-via feedback/set-email-target-address (:feedback.target.email props))
    (set-prop-via i18n/set-available-languages (:langs.available props))
    (set-prop-via env/set-ga-key (:tarkkailija.ga.key props))
    (set-prop-via leiki/set-leiki-host! (:leiki.url props))
    (gis/set-map-servers! {:map-server (:gis.mapserver.url props)
                           :minimap-server (:gis.minimap.url props)})
    (gis/set-map-server-authentication! {:mml-username (:wmts.raster.username props)
                                         :mml-password (:wmts.raster.password props)})))

(defn migration-dir []
  (env/property "migration.dir" (:migration.dir *props*)))

(defn wrap-to-js-namespace-var [name data]
  (str "var tarkkailija = tarkkailija || {};" name " = " (json/encode data) ";"))

(defn load-js-configs []
  (wrap-to-js-namespace-var
    "tarkkailija.configuration"
    {:mode (env/mode)
     :gaKey env/*ga-key*
     :i18n (i18n/get-localizations)
     :langs i18n/*available-langs*
     :leiki leiki/*leiki-host*
     :notifications (notification/notifications)}))