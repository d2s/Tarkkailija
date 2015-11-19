(ns tarkkailija.html-loader
  (:use [tarkkailija.env])
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(defn sanitize-path [path]
  (clojure.string/replace path "../" ""))

(defn load-public-res [file]
  (if-let [res (clojure.java.io/resource (str "public" (sanitize-path file)))]
    (slurp res)
    (do
      (log/warn "Unable to find requested file: " file)
      (throw (ex-info "File not found" {:type :not-found})))))

(def load-public-res-cached (memoize load-public-res))

(defn append-version-to [type]
  (fn [node] (update-in node [:attrs type] #(str % "?version=" (:buildtime build-info)))))

(defn load-html-with-res-versioning [file]
  (let [html      (enlive/html-resource (java.io.StringReader. (load-public-res file)))
        versioned (reduce
                    (fn [n f] (f n))
                    html
                    (list
                      #(enlive/at % [[:script (enlive/attr= :type "text/javascript")]] (append-version-to :src))
                      #(enlive/at % [[:script (enlive/attr= :type "application/javascript")]] (append-version-to :src))
                      #(enlive/at % [[:link (enlive/attr= :rel "stylesheet")]] (append-version-to :href))))]
    (apply str (enlive/emit* versioned))))

(def load-html-with-res-versioning-cached (memoize load-html-with-res-versioning))

(defn load-index []
  (if (dev-mode?)
    (load-html-with-res-versioning "/app/index.html")
    (load-html-with-res-versioning-cached "/app/index-all.html")))

(defn load-html-file [path]
  (if (dev-mode?)
    (load-public-res path)
    (load-public-res-cached path)))

(defn append-version-to-css-images [css]
  (s/replace
     css
     #"url[\s]*\([\s^\"]*([^\)^\"]*)\"[\s]*\)"
     (str "url(\"$1?version=" (:buildtime build-info) "\")")))

(defn load-main-css []
  (append-version-to-css-images
    (if (dev-mode?)
      (load-public-res "/app/css/main.css")
      (load-public-res-cached "/app/css/main.css"))))