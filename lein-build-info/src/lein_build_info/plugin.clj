(ns lein-build-info.plugin)

(defn build-prefix [root prefix]
  (str (if (.endsWith root "/") root (str root "/")) prefix))

(defn write-build-info [project path]
  (let [f (clojure.java.io/file path "build_info.clj")]
    (clojure.java.io/make-parents f)
    (spit f {:version (:version project) :buildtime (System/currentTimeMillis)})))

(defn middleware [project]
  (let [path (build-prefix (:compile-path project) "resources")]
    (write-build-info project path)
    (-> project
      (update-in [:resource-paths] conj (build-prefix (:compile-path project) "resources")))))