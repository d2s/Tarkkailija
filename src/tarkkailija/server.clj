(ns tarkkailija.server
  (:use clojure.tools.logging
        tarkkailija.mongo)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [sade.mongo :as mongo]
            [sade.security-headers :as headers]
            [tarkkailija.config-reader :as conf]
            [tarkkailija.logging :as logging]
            [tarkkailija.web]
            [tarkkailija.feed]
            [tarkkailija.env :as env]
            [tarkkailija.mongo-indices :as indices]
            [tarkkailija.batchrun :as batchrun]
            [tarkkailija.migration :as migration])
  (:gen-class))

(defn start-server [port]
  (with-logs "tarkkailija.server"
    (let [srv (server/start port {:mode (env/mode) :ns 'tarkkailija.web})]
      (info "Server running")
      srv)))

(defn stop-server [server]
  (server/stop server)
  (info "Server stopped"))

(defn -main [& _]
  (infof "Starting Tarkkailija version %s - built %s"
         (:version env/build-info)
         (.toString (org.joda.time.DateTime. (:buildtime env/build-info)) "dd.MM.yyyy HH:mm:ss"))
  (info "Server starting")
  (infof "Running on Java %s %s %s (%s)"
    (System/getProperty "java.vm.vendor")
    (System/getProperty "java.vm.name")
    (System/getProperty "java.runtime.version")
    (System/getProperty "java.vm.info"))
  (infof "Running on Clojure %d.%d.%d"
    (:major *clojure-version*)
    (:minor *clojure-version*)
    (:incremental *clojure-version*))
  (conf/init-configs (env/mode))
  (mongo/connect!)
  (when (env/dev-mode?)
    (mongo/clear!)
    (let [port 8001]
      (warn "Starting nrepl on port " port)
      (nrepl/start-server :port port)))
  (migration/with-migration-dir
    (conf/migration-dir)
    (migration/migrate-to! (:version env/build-info)))
  (indices/ensure-indices!)
  (batchrun/start-watch-email-scheduler)
  (server/add-middleware headers/session-id-to-mdc)
  (server/add-middleware headers/add-security-headers)
  (start-server (env/port)))
