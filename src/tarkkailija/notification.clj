(ns tarkkailija.notification
  (:use [noir.core :only [defpage]]
        [sade.web])
  (:require [cheshire.core :as json]
            [clojure.walk :as walk]))

(def ^:private notification (atom []))

(defjson [:get "/system/notification"] []
  @notification)

(defn parse-json-data [json]
  (if (string? json)
    (walk/keywordize-keys (json/parse-string json))
    json))

(defjson [:post "/system/notification"] {json :json}
  (if-let [{fi :fi sv :sv} (parse-json-data json)]
    (swap! notification conj {:time (java.lang.System/currentTimeMillis) :fi fi :sv sv})))

(defjson [:delete "/system/notification"] []
  (swap! notification (constantly [])))

(defn notifications []
  @notification)