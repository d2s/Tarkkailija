(ns tarkkailija.mongo-indices
  (:use clojure.tools.logging)
  (:require [monger.collection :as mc]))

(defn ensure-indices! []
  (info "Ensuring Mongo indices and building if necessary")
  (mc/ensure-index "users" {:email 1} {:unique true})
  (mc/ensure-index "watches" {:name 1 :_created 1}))
