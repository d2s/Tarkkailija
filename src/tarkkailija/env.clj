(ns tarkkailija.env
  (:use [clojure.tools.logging])
  (:require [clojure.string :as s]))

(defn property
  "Reads system property or system env or uses default."
  [key default]
  (or (System/getProperty (name key)) (System/getenv (name key)) default))

(defn mode [] (keyword (property "tarkkailija.mode" "dev")))
(defn port [] (Integer/parseInt (property "tarkkailija.port" "8080")))
(def log-dir (property "tarkkailija.logdir" "target"))

(def email-from "tarkkailija@solita.fi")

(defn dev-mode? []
  (= :dev (mode)))

(def build-info
  (do
    (if-let [f (clojure.java.io/resource "build_info.clj")]
      (read-string (slurp f))
      {:version (System/getProperty "tarkkailija.version") :buildtime (System/currentTimeMillis)})))

(def ^:dynamic ^:String *ga-key* (property "tarkkailija.ga.key" nil))

(defn set-ga-key [key]
  (alter-var-root #'*ga-key* (constantly key)))