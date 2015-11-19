(ns tarkkailija.logging
  (:use [clojure.tools.logging]
        [clj-logging-config.log4j])
  (:require [tarkkailija.env :as env]
             [clojure.java.io :as io])
  (:import [org.apache.log4j DailyRollingFileAppender EnhancedPatternLayout]))

(def pattern "%-7p %d (%r) [%X{sessionId}] %c:%L - %m%n")

(defn rolling-file [file]
  (DailyRollingFileAppender.
    (EnhancedPatternLayout. pattern)
    (.getPath (io/file env/log-dir file))
    "'.'yyyy-MM-dd"))

(def appender
  (if (env/dev-mode?)
    :console
    (rolling-file "server.log")))

(set-loggers! :root {:level   :info
                     :out     appender
                     :pattern pattern})
