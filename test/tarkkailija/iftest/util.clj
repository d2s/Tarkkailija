(ns tarkkailija.iftest.util
  (:use clj-webdriver.taxi
        clj-webdriver.remote.server
        midje.sweet
        sade.client
        tarkkailija.server
        tarkkailija.mongo
        [clj-webdriver.driver :only [init-driver]])
  (:require [tarkkailija.config-reader :as conf]
            [tarkkailija.env :as env]
            [cheshire.core :as json]
            [sade.mongo :as mongo]
            [clj-http.client :as http]
            [sade.client :as client])
  (:import [org.openqa.selenium.phantomjs PhantomJSDriver]
           [org.openqa.selenium.remote DesiredCapabilities]))

(defn parse-body [req]
  (json/parse-string (:body req) true))

(defn test-mongo-uri [] (System/getProperty "test_mongo_uri" "mongodb://127.0.0.1/tarkkailija-test"))

(def ^:private browser-count (atom 0))

;; tutki tata: https://github.com/semperos/clj-webdriver/blob/master/test/clj_webdriver/test/remote_existing.clj
(defn webdriver []
  (let [url (System/getProperty "webdriver_grid")]
;    (println "\n url: " url "\n")
    (if (-> url nil? not)
      (let [remote-session (new-remote-session
                             {:port 4444
                              :host url
                              :existing true}
                             {:browser :firefox})]
        (second remote-session))
;      {:browser :firefox}
      ; following will initialize phantomjs but apparently latest phantomjs doesn't work well with ghostdriver
      ; until updated ghostdriver version is available
      (init-driver {:webdriver (PhantomJSDriver. (DesiredCapabilities.))}))))

(defn browser-up!
  "Start up a browser if it's not already started."
  []
  (when (= 1 (swap! browser-count inc))
    (set-driver! (webdriver))
    (implicit-wait 5000))
  @browser-count)

(defn browser-down!
  "If this is the last request, shut the browser down."
  [& {:keys [force] :or {force false}}]
  (when (<= (swap! browser-count (if force (constantly 0) dec)) 0)
    (quit)
    (reset! browser-count 0))
  @browser-count)

(def ^:private server-count (atom 0))
(def ^:private server-atom (atom nil))

; wrapping sequence, which dechucks lazy sequences in such a way that
; only one element at a time is evaluated (normally lazy sequences are
; evaluated 32 elements at a time)
(defn seq1 [#^clojure.lang.ISeq s]
  (reify clojure.lang.ISeq
    (first [_] (.first s))
    (more [_] (seq1 (.more s)))
    (next [_] (let [sn (.next s)] (and sn (seq1 sn))))
    (seq [_] (let [ss (.seq s)] (and ss (seq1 ss))))
    (count [_] (.count s))
    (cons [_ o] (.cons s o))
    (empty [_] (.empty s))
    (equiv [_ o] (.equiv s o))))

(defn port-available? [port]
  (try
    (let [s (java.net.Socket. "localhost" port)]
      (.close s))
    false ; connection open, port is *not* available!
    (catch Exception e
      true)))

(defn find-free-port []
  (first (filter port-available? (seq1 (range 8081 10000)))))

(defn server-up!
  "Start the server if local server is configured to be used and it is not already running"
  []
  (when (and (client/is-local-server) (= 1 (swap! server-count inc)))
    (mongo/connect! (test-mongo-uri))
    (conf/init-configs (env/mode))
    (let [port (find-free-port)]
      (System/setProperty "tarkkailija.port" (str port))
      (reset! server-atom (tarkkailija.server/start-server port))))
  @server-count)

(defn server-down!
  "Shuts the local server down if it is running if this was the last request or forced"
  [& {:keys [force] :or {force false}}]
  (when (and (client/is-local-server) (<= (swap! server-count (if force (constantly 0) dec)) 0))
    (let [srv @server-atom]
      (when (-> srv nil? not)
        (tarkkailija.server/stop-server srv)
        (mongo/disconnect!)
        (reset! server-atom nil))
      (reset! server-count 0)))
  @server-count)

(defn connect-test-mongo
  "Connect test db with mongo-protocol"
  [] (mongo/connect! (test-mongo-uri)))

(defn clear-db!
  "clear the database via rest call."
  []
  (http/get (uri "/dev/api/fixture/clear-db") {:insecure? true}))

(defn disable-email-sending! []
  (http/get (uri "/dev/api/email-sending/disable")))

(defn enable-email-sending! []
  (http/get (uri "/dev/api/email-sending/enable")))

(defn fixture-minimal!
  "Initialize minimal fixture for tests"
  [] (http/post (uri "/dev/fixture/minimal") {:insecure? true}))

(defmacro with-db-cleared [& body]
  `(against-background [(before :contents (connect-test-mongo))
                        (before :facts (sade.mongo/clear!))]
                       (do ~@body)))

(defmacro with-browser [& body]
  `(against-background [(before :contents (browser-up!))
                        (after  :contents (browser-down!))]
                       (delete-all-cookies)
                       (do ~@body)))

(defmacro with-server [& body]
  `(against-background [(before :contents (disable-email-sending!))
                        (before :contents (server-up!))
                        (after :contents (server-down!))]
                       (do ~@body)))

(defmacro with-server-and-browser [& body]
  `(against-background [(before :contents (disable-email-sending!))
                        (before :contents (server-up!))
                        (before :contents (browser-up!))
                        (after :contents (server-down!))
                        (after :contents (browser-down!))]
                       (do ~@body)))

(defmacro with-server-db-cleared [& body]
  `(against-background [(before :contents (disable-email-sending!))
                        (before :contents (server-up!))
                        (before :facts (clear-db!))
                        (after :contents (server-down!))]
                       (do ~@body)))

(defmacro with-server-and-browser-db-cleared [& body]
  `(against-background [(before :contents (disable-email-sending!))
                        (before :contents (browser-up!))
                        (before :contents (server-up!))
                        (before :facts (clear-db!))
                        (after :contents (server-down!))
                        (after :contents (browser-down!))]
                       (do ~@body)))
