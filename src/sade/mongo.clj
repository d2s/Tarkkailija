(ns sade.mongo
  (:use [monger.operators]
        [monger.result]
        [clojure.tools.logging])
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [monger.query :as q]
            [sade.status :refer [defstatus]])
  (:import [org.bson.types ObjectId]
           [com.mongodb WriteConcern]))

;;
;; MongoDB uri
;;

(def ^:dynamic ^String *mongouri* "mongodb://127.0.0.1/test")
(def ^:dynamic ^WriteConcern *mongodb-write-concern* WriteConcern/SAFE)

(defn set-mongo-uri! [uri]
  (alter-var-root #'*mongouri* (constantly uri)))

;;
;; Utils
;;

; dynamic to allow mocking via binding
(def ^{:dynamic true
       :doc "timestamp in millis"}
  now (fn [] (System/currentTimeMillis)))

(defn ^String create-id
  "new org.bson.types.ObjectId().toString()"
  [] (.toString (ObjectId.)))

(defn versioned
  "adds mongo modifiers to increment _version and set _modified"
  [document]
  (assoc
    document
    $inc (merge (document $inc) {:_version 1})
    $set (merge (document $set) {:_modified (now)})))

(defn created
  "sets _id (create-id) if not set, _version (0), _created (now) and _modified (now)."
  [document]
  (let [now (now)
        id  (create-id)]
    (merge
      {:_id id}
      document
      {:_version  0}
      {:_created  now
       :_modified now})))

(defn without-system-keys
  "removes all keys starting with _"
  [m] (let [keys (filter #(.startsWith (name %) "_") (keys m))]
        (apply dissoc m keys)))

;;
;; Database Api
;;

(defn ^clojure.lang.IPersistentMap find-one
  "Returns first document or projection matching the query."
  ([collection query]
    (mc/find-one-as-map collection query))
  ([collection query projection]
    (mc/find-one-as-map collection query projection)))

(defn ^clojure.lang.IPersistentMap find-many
  "Returns all documents or projections matching the query."
  ([collection]
    (find-many collection {}))
  ([collection query]
    (find-many collection query {}))
  ([collection query projection]
    (mc/find-maps collection query projection)))

(defn ^clojure.lang.IPersistentMap find-many-with-limit
  "Returns documents matching query, up to given limit"
  [collection query limit]
  (q/with-collection (name collection)
    (q/find query)
    (q/limit limit)))

(defn ^clojure.lang.IPersistentMap find-many-with-pagination
  "Returns documents matching query with pagination. Note! Uses skip+limit,
   thus slow on large collections. With large collections use range queries."
  [collection query limit skip sort]
  (q/with-collection (name collection)
    (q/find query)
    (q/sort sort)
    (q/limit limit)
    (q/skip skip)))

(defn ^Integer count-documents
  [collection query]
  (mc/count collection query))

(defn ^Boolean insert-one
  "Inserts document into collection. Returns true if one document is inserted
   without errors, otherwise returns false."
  [collection document]
  (let [doc    (created document)
        result (mc/insert collection doc)
        ok     (ok? result)]
    ok))

(defn ^clojure.lang.IPersistentMap insert-one-and-return
  "Inserts document into collection. Returns created document or nil."
  [collection document]
  (let [doc    (created document)
        result (mc/insert-and-return collection doc)]
    result))

(defn ^Boolean update-one
  "Updates first document in collection matching conditions. Returns true if one document is
     updated without errors, otherwise returns false."
  [collection conditions document]
  (let [doc    (versioned document)
        result (mc/update collection conditions doc)
         ok     (ok? result)
        n      (.getN result)]
    (and ok (= n 1))))

(defn ^Boolean update-one-and-return
  "Updates first document in collection matching conditions. Returns updated document or nil."
  [collection conditions document & {:keys [fields sort remove upsert] :or {fields nil sort nil remove false upsert false}}]
  (mc/find-and-modify collection conditions (versioned document) :return-new true :upsert upsert :remove remove :sort sort :fields fields))

(defn ^Boolean update-many
  "Updates all documents in collection matching conditions. Returns true if documents are
   updated without errors, otherwise returns false."
  [collection conditions document]
  (let [result (mc/update collection conditions document :multi true)
        ok     (ok? result)
        n      (.getN result)]
    (and ok (> n 0))))

(defn ^Boolean delete-by-id
  "Deletes one document from collection by id. Returns true if document is removed
   without errors, otherwise returns false."
  [collection id]
  (let [result (mc/remove-by-id collection id)
        ok     (ok? result)]
    ok))

(defn ^clojure.lang.IPersistentMap aggregate
  "Performs an aggregate operation, see http://docs.mongodb.org/manual/reference/aggregation/"
  [collection stages]
  (mc/aggregate collection stages))

;;
;; Bootstrapping
;;

(defn connect!
  ([]
    (connect! *mongouri*))
  ([uri]
    (debug "Connecting to MongoDB: " uri)
    (m/connect-via-uri! uri)
    (debug "Database is: " (str (m/get-db)))))

(defn disconnect! []
  (try
    (m/disconnect!)
    ; silently ignore exceptions, can really happen only when the connection
    ; is already closed
    (catch Exception e)))

(defn test-connection []
  (try
    (not (nil? (m/command {:getLastError 1})))
    (catch Exception e
      false)))

(defn clear! []
  (warn "Clearing MongoDB: " *mongouri*)
  (db/drop-db (m/get-db)))

(defn connect-local!
  "connects to local mongodb-instance with db as name"
  [db] (connect! (str "mongodb://127.0.0.1/" (name db))))

;;
;; status
;;

(defstatus :db (test-connection))
