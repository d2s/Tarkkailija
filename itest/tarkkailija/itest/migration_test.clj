(ns tarkkailija.itest.migration-test
  (:use [midje.sweet]
        [tarkkailija.iftest.util]
        [tarkkailija.migration])
  (:require [monger.collection :as mc]))

(with-migration-dir "./migration-test"
  (with-db-cleared
    
    (fact "Migration without version data runs all migration files"
      (migrate-to! "1.0") => {:from nil :to "1.0" :executed-scripts 1 :schema-version "1.0"}
      (provided
        (check-schema-version) => nil
        (find-migration-scripts {:from nil :to "1.0"}) => [{:schema "1.0" :script (fn [] (mc/insert :foo {:bar 1}))}])
      (dissoc (mc/find-one-as-map :foo {}) :_id) => {:bar 1})
  
    (fact "Schema version check returns the value of version document"
      (mc/insert :schema {:version "1.0"})
    
      (check-schema-version) => "1.0")
    
    (fact "Migration updates schema version"
      (check-schema-version) => nil
      (migrate-to! "1.0") => {:from nil :to "1.0" :executed-scripts 1 :schema-version "1.0"}
      (provided
        (find-migration-scripts {:from nil :to "1.0"}) => [{:schema "1.0" :script (fn [] (mc/insert :foo {:bar 1}))}])
      (check-schema-version) => "1.0")
  
    (fact "Migration from version to same doesn't execute anything"
      (migrate-to! "1.0") => {:from "1.0" :to "1.0" :executed-scripts 0 :schema-version "1.0"}
      (provided
        (check-schema-version) => "1.0"))
  
    (fact "Script fetching finds all scripts from given delta in order"
      (let [to-decimal              (fn [s] (BigDecimal. (:schema s)))
            validate-schema-version (as-checker (fn [scripts]
                                                  (if (= 0 (count scripts)) false
                                                    (filter #(<= % 1.0M) (map to-decimal scripts)))))
            check-version-order     (as-checker (fn [scripts]
                                                  (let [versions (map :schema scripts)]
                                                    (= versions (sort versions)))))]
        (find-migration-scripts {:from "0.1" :to "1.0"}) => validate-schema-version
        (find-migration-scripts {:from "0.1" :to "1.0"}) => check-version-order))))