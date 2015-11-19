(ns sade.xml
  (:require [clojure.xml :as xml]
            [net.cgrand.enlive-html :as enlive]
            [clj-http.client :as http]))

(defn parse-string [#^java.lang.String s] (xml/parse (java.io.ByteArrayInputStream. (.getBytes s "UTF-8"))))
(defn parse-url [#^java.lang.String s] (parse-string (:body (http/post s {:conn-timeout 10000 :socket-timeout 15000 :insecure? true}))))
(defn parse [#^java.lang.String s]
  (let [trimmed (.trim s)]
    (cond
      (or (.startsWith trimmed "http://") (.startsWith trimmed "https://")) (parse-url s)
      (.startsWith trimmed "<") (parse-string s)
      :else (xml/parse s))))

(defn attr [xml] (:attrs xml))
(defn text [xml] (-> xml :content first))

(def has enlive/has)
(defn has-text [text] (enlive/text-pred (partial = text)))

(defn select [xml & path] (enlive/select xml (-> path vector flatten)))
(defn select1 [xml & path] (first (apply select xml path)))

(defn extract [xml m] (into {} (for [[k v] m] [k (->> v butlast (apply select1 xml) ((last v)))])))
(defn children [xml] (:content xml))
(defn convert [xml m] (map #(extract % m) (when (-> xml nil? not) (-> xml vector flatten))))
(defn fields-as-text [coll] (into {} (for [v coll] [v [v text]])))

;;
;; lossless XML to EDN simplification (from metosin with love)
;;

(declare xml->edn)
(defn- attr-name [k] (keyword (str "#" (name k))))
(defn- decorate-attrs [m] (zipmap (map attr-name (keys m)) (vals m)))
(defn- merge-to-vector [m1 m2] (merge-with #(flatten [%1 %2]) m1 m2))
(defn- childs? [v] (map? (first v)))
(defn- lift-text-nodes [m] (if (= (keys m) [:##text]) (val (first m)) m))
(defn- parts [{:keys [attrs content]}]
  (merge {}  #_(decorate-attrs attrs)
         (if (childs? content)
           (reduce merge-to-vector (map xml->edn content))
           (hash-map :##text (first content)))))

(defn xml->edn [xml] (hash-map (:tag xml) (-> xml parts lift-text-nodes)))
