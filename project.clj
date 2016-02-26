(defproject tarkkailija "1.4.5-SNAPSHOT"
  :description "Tarkkailija"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.nrepl "0.2.2"]
                 [noir "1.3.0" :exclusions [org.clojure/clojure ring]] ;; does not work with 1.5
                 [ring "1.1.8"]
                 [enlive "1.1.1" :exclusions [org.clojure/clojure]]
                 [org.clojure/data.zip "0.1.1"]
                 [clj-http "0.6.5" :exclusions [commons-codec]]
                 [clj-time "0.4.4"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [org.clojure/tools.logging "0.2.6"]
                 [clj-logging-config "1.9.10" :exclusions [log4j]]
                 [org.slf4j/slf4j-log4j12 "1.7.2"]
                 [com.novemberain/monger "1.5.0-beta1"]
                 [com.draines/postal "1.9.2"]
                 [org.clojure/data.xml "0.0.7"]
                 [net.sf.uadetector/uadetector-resources "2013.07"]
                 [org.clojure/java.data "0.1.1"]
                 [ontodev/excel "0.2.0" :exclusions [[xml-apis]]]]
  :profiles {:dev   {:resource-paths ["dev-config"]
                     :dependencies [[midje "1.5.0" :exclusions [org.clojure/clojure]]
                                    [clj-webdriver "0.6.0" :scope "test" :exclusions [org.seleniumhq.selenium/selenium-server]]  ;; "0.7.0-SNAPSHOT"  (what repo contains this?)
                                    ;; http://docs.seleniumhq.org/download/maven.jsp
                                    [org.seleniumhq.selenium/selenium-java "2.39.0" :scope "test" :exclusions [org.eclipse.jetty/jetty-http org.eclipse.jetty/jetty-io]]  ;; 2.9.0
                                    [org.seleniumhq.selenium/selenium-server "2.39.0" :scope "test" :exclusions [org.eclipse.jetty/jetty-http org.eclipse.jetty/jetty-io]]  ;; 2.9.0
                                    ;; https://github.com/detro/ghostdriver
                                    [com.github.detro.ghostdriver/phantomjsdriver "1.1.0"]
;                                    [com.github.detro/phantomjsdriver "1.2.0"]
                                    ]
                     :plugins [[lein-midje "2.0.1"]
                               [lein-buildid "0.1.0"]
                               [lein-embongo "0.2.0"]
                               [lein-js-compiler "0.1.0-SNAPSHOT"]
                               [lein-build-info "0.1.0-SNAPSHOT"]]
                     :embongo {:version "2.2.3"}}
             :test     {:resource-paths ["prod-config"] :injections [#_(System/setProperty "tarkkailija.mode" "test")]}
             :prod     {:resource-paths ["prod-config"] :injections [(System/setProperty "tarkkailija.mode" "prod")]}
             :itest    {:test-paths ^:replace ["itest"] :source-paths ["test"]}
             :stest    {:test-paths ^:replace ["stest"] :source-paths ["test"]}
             :ftest    {:test-paths ^:replace ["ftest"] :source-paths ["test"]}
             :alltests {:source-paths ["itest" "stest" "ftest"]}
             :remote-webdriver {:injections [(System/setProperty "webdriver_grid" "192.168.7.223")]}   ;; TODO: confirm the IP
             :local {:jvm-opts ["-Dtarget.server=http://localhost:8080"]}
             :tarkkailijadev {:jvm-opts ["-Dtarget.server=https://test.etarkkailija.fi"]
                              :injections [(System/setProperty "test_mongo_uri" "mongodb://localhost/tarkkailija-test")]}
             :tarkkailijatest {:jvm-opts ["-Dtarget.server=https://qa.etarkkailija.fi"]
                               :injections [(System/setProperty "test_mongo_uri" "mongodb://localhost/tarkkailija-test")]}}
  :jar-exclusions [#".DS_Store"]
  :repositories [["solita-archiva" {:url "http://mvn.solita.fi/repository/solita" :checksum :ignore}]]
  :plugin-repositories [["solita-archiva" {:url "http://mvn.solita.fi/repository/solita" :checksum :ignore}]]
  :aliases {"verify"      ["embongo" "with-profile" "dev,alltests" "midje"]
            "integration" ["embongo" "with-profile" "dev,itest" "midje"]}
  :auto-clean false
  :main tarkkailija.server
  :js-resources {:html          ["resources/public/app/index.html"]
                 :exclude       [#".*s7.addthis.com.*" #".*config.js.*"]
                 :prefix        "public/app/"
                 :output        "public/app/all.js"
                 :html-output   "public/app/index-all.html"
                 :compile-level :simple}
  :middleware [lein-js-compile.plugin/middleware lein-build-info.plugin/middleware]
  :repl-options {:init-ns tarkkailija.server}
  :min-lein-version "2.0.0")
