(ns sade.client
  (:use [clojure.walk :only [keywordize-keys]]
        [clojure.tools.logging])
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [tarkkailija.env :as env]))

(def ^:const accepts "application/json;charset=utf-8")

(defn local-addresses []
  (->> (java.net.NetworkInterface/getNetworkInterfaces)
    enumeration-seq
    (map bean)
    (filter (complement :loopback))
    (mapcat :interfaceAddresses)
    (map #(.. % (getAddress) (getHostAddress)))))

(defn ipv4? [ip] (re-matches #"\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}" ip))

(defn server-address [] (System/getProperty "target.server" (str "http://" (or (some ipv4? (local-addresses)) "localhost") ":" (env/port))))
(defn uri [& path] (apply str (server-address) path))

(defn is-local-server [] (nil? (System/getProperty "target.server")))

(defn- parse-json-on-200 [response]
  (let [status (:status response)]
    (if (= status 200)
      (-> (:body response) (json/decode) (keywordize-keys))
      (warn "got invalid status " status " from response: " response))))

(defn json-get ([url] (json-get url {}))
  ([url data]
    (let [response (client/get url
                     {:headers {"accepts" accepts}
                      :throw-exceptions false
                      :query-params data
                      :insecure? true})]
      (parse-json-on-200 response))))

(defn- create-request [apikey json]
  {:headers {"authorization" (str "apikey=" apikey)
             "accepts" accepts}
   :content-type "application/json;charset=utf-8"
   :throw-exceptions false
   :body (json/encode json)
   :insecure? true})

(defn json-post
  ([url data]
    (let [response (client/post url
                     {:headers {"accepts" accepts}
                      :throw-exceptions false
                      :content-type :json
                      :body (json/encode data)
                      :insecure? true})]
    (parse-json-on-200 response)))
  ([url data apikey]
    (let [response (client/post url (create-request apikey data))]
      (parse-json-on-200 response))))

(defn json-put [url data apikey]
  (client/put url (create-request apikey data)))

(defn http-get [url apikey & args]
  (let [response (client/get url
                             {:headers {"authorization" (str "apikey=" apikey)
                                        "accepts" accepts}
                              :throw-exceptions false
                              :query-params (apply hash-map args)
                              :insecure? true})]
    (parse-json-on-200 response)))

(defn plain-get [url apikey & args]
  (client/get url
              {:headers {"authorization" (str "apikey=" apikey)
                         "accepts" accepts}
               :throw-exceptions false
               :query-params (apply hash-map args)
               :insecure? true}))

(defn http-post
  ([url]
    (http-post url {}))
  ([url data]
    (let [response (client/post url
                     {:headers {"accepts" accepts}
                      :throw-exceptions false
                      :form-params  data
                      :insecure? true})]
    (parse-json-on-200 response))))

(defn http-delete [url apikey & args]
  (client/delete url (create-request apikey args)))

(defn plain-put [url apikey & args]
  (client/put url
              {:headers {"authorization" (str "apikey=" apikey)
                         "accepts" accepts}
               :throw-exceptions false
               :query-params (apply hash-map args)
               :insecure? true}))
