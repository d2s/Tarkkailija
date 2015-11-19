(ns sade.mongo-test
  (:use clojure.test
        midje.sweet
        sade.mongo))

(fact
  (create-id) => string?)

(fact
  (without-system-keys {:_a 1 :b 2 :_c 3}) => {:b 2})

(fact
  (against-background
    (now) => 123
    (create-id) => 999)
  (created {}) => (just {:_version 0
                         :_id 999
                         :_modified 123
                         :_created 123}))

(fact
  (against-background (now) => 123)
  (versioned {}) => (just {"$inc" {:_version 1}}
                          {"$set" {:_modified 123}})
  (versioned {"$inc" {:a 1}
              "$set" {:b 2}}) => (just {"$inc" {:_version 1 :a 1}}
                                       {"$set" {:_modified 123 :b 2}}))
