(ns sade.useragent
  (:require [clojure.java.data :as data])
  (:import [net.sf.uadetector.service.UADetectorServiceFactory]))

(defn parse [#^String agent]
  (when-let [orig (data/from-java (.parse (net.sf.uadetector.service.UADetectorServiceFactory/getResourceModuleParser) agent))]
    (merge
      (dissoc orig :icon :url :producerUrl)
      {:operatingSystem (dissoc (:operatingSystem orig) :url :producerUrl :producer :icon)})))

(def parse-cached (memoize parse))

(defn parse-user-agent [request]
  (when-let [ua ((comp #(get % "user-agent") :headers) request)]
    (parse-cached ua)))
