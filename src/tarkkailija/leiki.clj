(ns tarkkailija.leiki
  (:use     [sade.xml]
            [clojure.tools.logging]
            [monger.operators])
  (:require [clojure.string :as s]
            [ring.util.codec :as codec]
            [clj-time.core :as time]
            [clj-time.format :as format]
            [net.cgrand.enlive-html :as enlive]
            [sade.mongo :as mongo]
            [clj-http.client :as http-client]
            [cheshire.core :as json]
            [sade.client :as client]
            [sade.status :refer [defstatus]]
            [clojure.walk :as walk]
            [tarkkailija.env :as env])
  (:import  [org.joda.time.DateTime]))

(defn feed-id [] (str "tarkkailija_feed_" (org.bson.types.ObjectId.)))

(def default-enddate-difference-days 7)
(def max-category-search-results 20)
(def time-format "yyyy-MM-dd HH:mm")
(defn parse-date [s] (format/parse time-format s))
(defn unparse-date [dt] (format/unparse time-format dt))
(defn ->date [s] (format/parse (format/formatter time-format) s))
(defn default-image [id] (str "/api/map-image/" (codec/form-encode id)))

(def leiki-dataset-type (atom "viranomaiset"))

(def ^:dynamic ^String *leiki-host* (env/property "leiki.server" "https://tarkkailija.leiki.com"))

(defn set-leiki-host! [host]
  (info "Setting Leiki host url to" host)
  (alter-var-root #'*leiki-host* (constantly host)))

(defn leiki-widget-uri [article-id] (str *leiki-host* "/focus/mwidget?lpage&section=all&wname=tarkkailija1&rid=" (codec/form-encode article-id)))
(defn tarkkailija-link-uri [article-id] (str (client/uri "/api/show-article?id=" (codec/form-encode article-id))))
(defn leiki-uri [] (str *leiki-host* "/focus/api"))
(defn leiki [m]
  (let [url (str (leiki-uri) "?" (codec/form-encode m))]
    (info "->leiki:" url)
    (parse url)))

(defn normalize-start-date [d]
  (.toDateTime (time/date-midnight (time/year d) (time/month d) (time/day d))))

(defn- normalize-end-date [d]
  (.minus (.toDateTime (time/date-midnight (time/year d) (time/month d) (time/day d))) (time/secs 1)))

(defn remove-nil-entries [m]
  (into {} (remove (comp nil? val) m)))

(defn ->points [xml]
  (-> xml
    (select [:value])
    (->>
      (map text)
      (map (fn [pair] (s/split pair #" ")))
      (map (fn [[x y]] {:x x :y y}))
      (filter (partial not= {:x "0.0" :y "0.0"})))))

(def ->leiki-item {:headline    [:headline text]
                   :id          [(comp :id attr)]
                   :sourcename  [:sourcename text]
                   :link        [:link text]
                   :picture     [:tags (enlive/attr= :name "sys_f_enclosure_url") :value text]
                   :location    [:tags (enlive/attr= :name "sys_f_location_point") ->points]
                   :description [:description text]
                   :content     [:content text]
                   :date        [:date text]})

(def ->leiki-category-item
  {:label [:cat (comp :name attr)]
   :value [:cat (comp :id attr)]})

(def ->leiki-area-item
  {:label [:cat (comp :name attr)]
   :value [:cat (comp :id attr)]
   :type  [:cat (comp
                  (fn [elem] (let [searchables ["street_section" "postcode" "municipality"]
                                   types       (map #(-> % :attrs :type) elem)]
                               (some #(some #{%} types) searchables)))
                  :content)]
   :coordinates [:cat (comp :coordinates attr)]})

(def ->leiki-category-profile
  {:id     [:cat (comp :id attr)]
   :name   [:cat (comp :name attr)]
   :weight [:cat (comp :weight attr)]})

(defn ok? [xml] (-> xml attr :stat (= "ok")))

(defn replace-nil-images-with-default [m]
  (let [f (fn [x]
          (if (nil? (:picture x))
            (merge x {:picture (default-image (:id x))})
            x))]
    (update-in m [:articles] #(map f %))))

(defn convert-links [m]
  (merge m {:articles (map #(merge % {:link (tarkkailija-link-uri (:id %))}) (:articles m))}))

(defn items
  ([xml] (items xml 0))
  ([xml offset]
    (when (ok? xml)
      (convert-links
        (replace-nil-images-with-default
          {:articles (into [] (drop offset (convert (children xml) ->leiki-item)))
           :totalresults (or (-> xml attr :totalresults) "0")})))))

(defn normalize-category [label value category]
  {label (s/capitalize (or (label category) ""))
   value (str "category_all_na_" (value category))})

(defn normalize-categories [cats label value]
  (map (partial normalize-category label value) cats))

(defn category-items [xml]
  (when (ok? xml)
    (normalize-categories
      (convert (enlive/select xml [:match :cat]) ->leiki-category-item)
      :label
      :value)))

(defn normalize-area [label value coordinates type category]
  {label (s/capitalize (or (label category) ""))
   value (str "category_all_na_" (value category))
   coordinates (coordinates category)
   type (s/capitalize (or (type category) ""))})

(defn normalize-areas [cats label value coordinates type]
  (map (partial normalize-area label value coordinates type) cats))

(defn area-items [xml]
  (when (ok? xml)
    (normalize-areas
      (convert (enlive/select xml [:match :cat]) ->leiki-area-item)
      :label
      :value
      :coordinates
      :type)))

(defn leiki-profile [xml]
  (when (ok? xml)
    (map
      (fn [c] (merge
                {:weight (java.lang.Long/parseLong (:weight c))}
                (normalize-category :name :id c)))
      (convert
        (enlive/select xml [[:cat]])
        ->leiki-category-profile))))

;;
;; Standard Apis
;;

(defn search-content [query]
  (-> {:method "searchc"
       :showsrcname true
       :t2_sys_f_location_present true
       :query query
       :max 10} leiki items))

(defn find-similar-content
  [{:keys [article-id startdate enddate sort limit offset maxtextlength]
    :or {startdate (. (time/now) minusDays default-enddate-difference-days) enddate (time/now) sort "rel" limit 10 offset 0 maxtextlength 100}}]
  (let [querymap {:method "findsc"
                  :cid article-id
                  :max limit
                  :startdate (. (normalize-start-date startdate) toString time-format)
                  :enddate (. (normalize-end-date enddate) toString time-format)
                  :sort sort
                  :showtotalresults true
                  :showtags true
                  :fulltext true
                  :maxtextlength maxtextlength
                  :similaritylimit 99
                  :matchlimit 25
                  :showsrcname true
                  :t2_sys_f_location_present true
                  :t_type @leiki-dataset-type}
        final-querymap (remove-nil-entries querymap)]
    (items (-> final-querymap leiki) offset)))

(defn find-similar-content-tarkkailija
  [{:keys [areas categories geojson startdate enddate limit offset maxtextlength]
    :or {areas [] categories [] geojson nil startdate (. (time/now) minusDays default-enddate-difference-days) enddate (time/now) limit 10 offset 0 maxtextlength 100}}]
  (let [id (feed-id)
        querymap {:method "findscTK"
                  :cid id
                  :fid "heratteet_testi"
                  :showsrcname true
                  :fulltext true
                  :maxtextlength maxtextlength
                  :similaritylimit 99
                  :matchlimit 2
                  :title id
                  :t_sys_f_extracid (if (> (count areas) 0) (apply conj categories areas) categories)
                  :showtags true
                  :startdate (. (normalize-start-date startdate) toString time-format)
                  :enddate (. (normalize-end-date enddate) toString time-format)
                  :max limit
                  :geometry geojson
                  :t2_type @leiki-dataset-type
                  :t2_sys_f_location_present true}
        final-querymap (remove-nil-entries querymap)]
    (items (-> final-querymap leiki) offset)))

(defn- nil-to-empty-str [s] (or s ""))

(defn- replace-empty-names-with-default [m]
  (let [pick-name (fn [k] (if (empty? (k m)) (:en m) (k m)))]
    {:fi (pick-name :fi)
     :sv (pick-name :sv)
     :en (:en m)}))

(defn ->category-i18n [xml]
  (let [select-names
        (fn [n]
          (replace-empty-names-with-default
            (first
              (convert n
                {:en [[(enlive/attr= :lang "en")] (comp s/capitalize nil-to-empty-str text)]
                 :fi [[(enlive/attr= :lang "fi")] (comp s/capitalize nil-to-empty-str text)]
                 :sv [[(enlive/attr= :lang "sv")] (comp s/capitalize nil-to-empty-str text)]}))))]
    (when (ok? xml)
      (convert (children xml)
        {:id    [:cat (comp #(str "category_all_na_" %) :id attr)]
         :names [select-names]}))))

(defn get-categories [& catids]
  (-> {:method "getcategories" :catids (s/join "," catids)} leiki ->category-i18n))

; we can memoize these since they are very rarely changed in Leiki
(def get-categories-memoized (memoize get-categories))

(defn combine-langs [items]
  (let [names (fn [e]
                (into {} (map (fn [%] [(keyword (:lang %)) (s/capitalize (:name %))]) e)))]
    (for [e items]
      {:id (:id (first e))
       :names (names e)})))

(defn add-or-update-content-item [id areas categories geojson]
  (let [query-map
        {:method "addcontent"
         :cid id
         :fid "heratteet_testi"
         :title id
         :t_sys_f_extracid (if (> (count areas) 0) (apply conj categories areas) categories)
         }]
    (if geojson
      (-> (conj query-map {:geometry geojson}) leiki ok?)
      (-> query-map leiki ok?))))

(defn remove-content-item [id]
  (-> {:method "rmitem"
       :cid id} leiki ok?))

(defn category-search [text lang]
  (let [query-map
        {:method "searchcategories"
         :text (str "leikiTarkkailijaaihepiirihaku " text)
         :lang lang
         :instancesearch true
         :negativeFilterID 597273
         :showEntities "ALL"}
        final-querymap (remove-nil-entries query-map)]
    (take max-category-search-results (-> final-querymap leiki category-items))))
(def category-search-memoized (memoize category-search))

(defn- area-search [text lang geojson]
  (let [positive-filter (if geojson 655883 597273)
        onlyDirectDescendants (if geojson true nil)
        query-map
        {:method "searchcategories"
         :text (str "leikiTarkkailijaaihepiirihaku " text)
         :lang lang
         :geometry geojson
         :instancesearch false
         :positiveFilterID positive-filter
         :onlyDirectDescendants onlyDirectDescendants
         :showEntities "ALL"}
        final-querymap (remove-nil-entries query-map)]
    (if geojson
      (-> final-querymap leiki area-items)
      (take max-category-search-results (-> final-querymap leiki area-items)))))

(defn area-search-with-text [text lang]
  (area-search text lang nil))

(defn area-search-with-geojson [geojson lang]
  (area-search nil lang geojson))

(defn get-article-data [articleId]
  (-> {:method "getcontent"
       :cid articleId
       :showsrcname true
       :t2_sys_f_location_present true
       :t_type @leiki-dataset-type} leiki items :articles first))

(defn exact-geojson-fetch [area-ids]
  (let [query-map {:method "getgeojson" :catids area-ids}
        result (-> query-map leiki)]
    (json/parse-string (-> (select1 result [:geojson]) text))))

;;
;; Business Apis
;;

(defn exists? [id] (-> id get-categories nil? not))

(defn get-feed-contents [id areas categories geojson]
  (when (add-or-update-content-item id areas categories geojson)
    (find-similar-content id)))

(defn create-feed [areas categories geoJson]
  (let [id     (feed-id)
        result (get-feed-contents id areas categories geoJson)]
    (when result id)))

(defn get-feed-contents-and-delete-feed [areas categories geojson]
  (let [id     (feed-id)
        result (get-feed-contents id areas categories geojson)]
    (future (remove-content-item id)) result))


;;
;; Status Apis
;;

(defn are-we-alive []
  (-> {:method "arewealive"} leiki ok?))

;;
;; Reporting APIs
;;

(defn get-average-profile [startdate enddate lang]
  (let [query-map
        {:method    "getaverageprofile"
         :type      "user"
         :startdate (. (normalize-start-date startdate) toString time-format)
         :enddate   (. (normalize-end-date enddate) toString time-format)
         :lang      lang}
        final-querymap (remove-nil-entries query-map)]
    (-> final-querymap leiki leiki-profile)))

(defn ->leiki-sources [xml]
  (when (ok? xml)
    (convert (children xml)
      {:name [:name text]
       :link [:link text]})))

(defn get-feed-source-for [type]
  {:pre [(#{"viranomaiset" "media"} type)]} ; only valid values
  (-> {:method "getfeeds"
       :t_type type
       :format "xml"} leiki ->leiki-sources))

(defn get-feed-sources []
  {:authorities (get-feed-source-for "viranomaiset")
   :media       (get-feed-source-for "media")})

;;
;; Non standard APIs
;;

(defn fetch-frontpage-categories-ids []
  (for [e (:content (parse (:body (http-client/get (str (leiki-uri) "?method=getheadlines&fid=CategorySearch&max=100")))))]
    (-> (select1 e :item) :attrs :id)))

(def fetch-frontpage-categories-ids-memoized (memoize fetch-frontpage-categories-ids))

(defn frontpage-categories []
  (apply get-categories-memoized (fetch-frontpage-categories-ids-memoized)))

;;
;; Extensions
;;

(defn merge-with-key [key & maps] (->> maps flatten (group-by key) vals (map (partial reduce merge))))

(defn enriched [articles]
  (let [keys      (map :id (:articles articles))
        meta-data (mongo/find-many :articles {:id {$in keys}})]
    (merge articles {:articles (merge-with-key :id (:articles articles) meta-data)})))

(defn personalized [articles email]
  (let [keys       (map :id (:articles articles))
        liked-keys (map :id (mongo/find-many :articles {:likers email} {:id 1}))
        personal   (map #(into {} {:id % :personal {:likes true}}) liked-keys)]
    (merge articles {:articles (merge-with-key :id (:articles articles) personal)})))

(defn has-meta-data? [id]
  (-> (mongo/find-one :articles {:_id id} {:_id 1}) nil? not))

;;
;; Status
;;

(defstatus :leiki (are-we-alive))
