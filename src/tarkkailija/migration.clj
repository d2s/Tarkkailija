(ns tarkkailija.migration
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [monger.query :as q]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(def ^:private ^:dynamic *migration-script-dir* "./migration")

(defmacro with-migration-dir [directory & body]
  `(binding [*migration-script-dir* ~directory]
     ~@body))

(defn strip-version-extension [s]
  (when (not (nil? s))
    (s/replace s #"(?s)-.*" "")))

(defn to-int [s]
  (Integer/parseInt s))

(defn str-contains [s1 s2]
  (> (.indexOf s1 s2) -1))

(defn parse-version [s]
  (when (not (nil? s))
    (let [parts (s/split s #"[.]")]
      (conj
        {:major  (to-int (first parts))}
        (when (> (count parts) 1)             {:minor  (to-int (second parts))})
        (when (> (count parts) 2)             {:hotfix (to-int (strip-version-extension (last parts)))})
        (when (str-contains (last parts) "-") {:extension (subs (last parts) (+ (.indexOf (last parts) "-") 1))})))))

(defn version-compare [v1 v2]
  (compare
    (into [] (map #(% v1) [:major :minor :hotfix]))
    (into [] (map #(% v2) [:major :minor :hotfix]))))

(defn check-schema-version []
  (:version (mc/find-one-as-map :schema {} [:version])))

(defn update-schema-version-to [version]
  (if-let [cur (check-schema-version)]
    (mc/update :schema {} {:version version :updated (java.lang.System/currentTimeMillis)})
    (mc/insert :schema {:version version :updated (java.lang.System/currentTimeMillis)}))
  version)

(defn load-migration-script [script]
  (merge
    (with-open [r (java.io.PushbackReader. (io/reader (.getAbsolutePath script)))]
      (binding [*read-eval* false]
        (load-reader r)))
    {:file (.getAbsolutePath script)}))

(defn file-extension [f]
  (let [i (.lastIndexOf (.getAbsolutePath f) ".")]
    (if (> i -1)
      (subs (.getAbsolutePath f) (+ i 1))
      "")))

(defn newer-version-than [v]
  (let [version (parse-version v)]
    #(> (version-compare (parse-version (:schema %)) version) 0)))

(defn older-or-same-version-than [v]
  (let [version (parse-version v)]
    #(<= (version-compare (parse-version (:schema %)) version) 0)))

(defn find-migration-scripts [delta]
  (->> (io/file *migration-script-dir*)
    .listFiles
    (filter #(and (.isFile %) (= "clj" (file-extension %))))
    (map load-migration-script)
    (filter (newer-version-than (:from delta))) ; drop too old scripts
    (filter (older-or-same-version-than (:to delta))) ; drop too new scripts 
    (sort-by :schema)))

(defn execute? [script]
  (not
    (if-let [cur (check-schema-version)]
      (<= (version-compare (parse-version (:schema script)) (parse-version cur)) 0))))

(defn execute-script [script]
  (when (execute? script)
    (log/infof "Executing migration script for schema version %s (%s)" (:schema script) (:file script))
    ((:script script)) ; execute the actual migration
    (update-schema-version-to (:schema script))))

(defn migrate-to! [version]
  (log/infof "Executing migration scripts from %s" *migration-script-dir*)
  (let [delta     {:from (check-schema-version) :to (strip-version-extension version)}
        versions (->> (find-migration-scripts delta)
                   (map execute-script)
                   (remove nil?))
        result   (merge delta {:executed-scripts (count versions)
                               :schema-version   (or (last versions) (:to delta))})]
    (if (> (:executed-scripts result) 0)
      (log/infof "Updated database from %s to %s, executed %s scripts"
        (:from result)
        (:schema-version result)
        (:executed-scripts result))
      (log/info "No migration scripts to execute, database up to date"))
    result))