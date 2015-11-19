(ns tarkkailija.users
  (:use [monger.operators])
  (:require [sade.mongo :as mongo]))

(defn find-user-by-id [user-id]
  (mongo/find-one :users {:_id user-id}))

(defn find-user [username]
  (mongo/find-one :users {:email username}))

(defn find-all-users []
  (mongo/find-many :users {}))

(defn count-users-by-creation-time [start-time end-time]
  (map
    (fn [%] {:_created (:_id %) :users (:users %)})
    (mongo/aggregate :users [{$match   {:_created {$gte start-time $lte end-time} :enabled true}}
                             {$sort    {:_created -1}}
                             {$project {:_created 1}}
                             {$group   {:_id "$_created" :users {$sum 1}}}])))

(defn count-users-to-date [d]
  (let [r (first (mongo/aggregate :users [{$match   {:_created {$lte d} :enabled true}}
                                          {$sort    {:_created -1}}
                                          {$project {:_created 1}}
                                          {$limit   1}]))]
     {:_created (:_created r)
      :users (mongo/count-documents :users {:_created {$lte (:_created r)} :enabled true})}))