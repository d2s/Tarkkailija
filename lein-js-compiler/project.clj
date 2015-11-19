(defproject lein-js-compiler "0.1.0-SNAPSHOT"
  :description "Leiningen plugin for compiling your Javascript code with Google Closure"
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [enlive "1.1.1" :exclusions [org.clojure/clojure]]
                 [com.google.javascript/closure-compiler "v20130227"]]
  :profiles {:dev {:dependencies [[midje "1.5.0" :exclusions [org.clojure/clojure]]]
                   :plugins [[lein-midje "2.0.1"]]}}
  :eval-in-leiningen true)
