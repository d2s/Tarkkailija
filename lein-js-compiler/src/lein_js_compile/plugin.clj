(ns lein-js-compile.plugin)

(defn build-prefix [root prefix]
  (str (if (.endsWith root "/") root (str root "/")) prefix))

(defn middleware [project]
  (-> project
    (update-in [:resource-paths] conj (build-prefix (:compile-path project) "resources"))))