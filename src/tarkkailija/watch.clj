(ns tarkkailija.watch
  (:use [monger.operators])
  (:require [sade.mongo :as mongo]
            [sade.i18n :as i18n]
            [tarkkailija.users :as users]
            [tarkkailija.leiki :as leiki])
  (:import [org.mindrot.jbcrypt BCrypt]))

(defn calculate-auth-token [watch]
  ;; Yes, this is kinda ugly way of using BCrypt, this doesn't provide that level of security
  ;; we'd really like to have. However, the data secured via this isn't all *that* sensitive
  ;; so this should be more than enough for securing watch listing data
  (apply str (drop (count "$2a$10$") (BCrypt/hashpw (str (:_created watch) "_tark_" (:id watch)) (str "$2a$10$" (:userid watch))))))

(defn clear-secure-data [w]
  (dissoc w :subscribeEmails :userid))

(defn enrich-names [w]
  (if (and (empty? (:categories w)) (empty? (:areas w)))
    w
    ;; either :categories or :areas is not-empty
    (let [ids         (cond
                        (empty? (:categories w)) (:areas w)
                        (empty? (:areas w)) (:categories w)
                        :else (apply conj (:categories w) (:areas w)))
          names       (apply leiki/get-categories-memoized ids)
          cat-update  (fn [e] (map (fn [a]
                                     (let [n (first (map :names (filter #(= (:id %) a) names)))]
                                       {:id a
                                        :name ((keyword (i18n/current-lang)) n)})) e))]
    (-> w
      (update-in [:categories] cat-update)
      (update-in [:areas] cat-update)))))

(defn generate-rss-token [w]
  (if (not (:publicFeed w))
    (assoc-in w [:rss-token] (calculate-auth-token w))
    w))

(defn- define-watch-post-operations [{clear-secure :clear-secure enrich :enrich rss-token :rss-token}]
  (apply comp (remove nil? [(when clear-secure clear-secure-data)
                            (when enrich enrich-names)
                            (when rss-token generate-rss-token)])))

(defn find-watch [watch-id & ops]
  (if-let [w (mongo/find-one :watches {:_id (name watch-id)})]
    ((define-watch-post-operations ops) w)
    (throw (ex-info (str "No such watch id=" watch-id) {:type :not-found}))))

(defn find-all-watches [& ops]
  (map (define-watch-post-operations ops) (mongo/find-many :watches)))

(defn find-all-watch-ids []
  (map :_id (mongo/find-many :watches {} [:_id])))

(defn find-all-watches-for-user [user-id & ops]
  (map (define-watch-post-operations ops) (mongo/find-many :watches {:userid user-id})))

(defn find-multiple-watches-for-user [user-id ^Integer limit & ops]
  (let [watches (mongo/find-many-with-limit :watches {:userid user-id} limit)]
    (map (define-watch-post-operations ops) watches)))

(defn find-public-watches
  ([^Integer limit ops] (find-public-watches limit 0 ops))
  ([^Integer limit ^Integer skip ops] (find-public-watches limit 0 "" ops))
  ([^Integer limit ^Integer skip ^String query ops] (find-public-watches limit skip query nil ops))
  ([^Integer limit ^Integer skip ^String query order-subscribe-user-id ops]
  (let [watches (mongo/find-many-with-pagination
                  :watches
                  {:publicFeed true :name {$regex (str query ".*") $options "i"}}
                  limit
                  skip
                  (sorted-map :_created -1))]
    (map (define-watch-post-operations ops) watches))))

(defn count-public-watches
  ([] (count-public-watches ""))
  ([query]
    (mongo/count-documents :watches {:publicFeed true :name {$regex (str query ".*") $options "i"}})))

(defn get-subscriptions-for-user [user-id]
  (let [subs (mongo/find-many :watches {:subscribeEmails.userId user-id})]
    (map
      #(merge 
         (select-keys % [:_id :name]) 
         {:own (= (:userid %) user-id)}
         (select-keys (first (filter (fn [s] (= user-id (:userId s))) (:subscribeEmails %))) [:lang :email]))
      subs)))

(defn get-email-subscriptions-of-watch [watch-id]
  (let [watch (find-watch watch-id)]
    (map #(merge % (when (nil? (:email %)) {:email (:email (users/find-user-by-id (:userId %)))})) (:subscribeEmails watch)) ))

(defn owner-for-watch [watch-id]
  (keyword (:userid (mongo/find-one :watches {:_id watch-id} [:userid]))))

(defn is-watch-owner [watch-id user-id]
  (= (owner-for-watch watch-id) (keyword user-id)))

(defn check-own-watch [watch-id user-id]
  (when (not (is-watch-owner watch-id user-id))
    (throw (ex-info (str "User not authorized to handle watch id=" watch-id) {:type :not-authorized}))))

(defn create-watch [user-id {:keys [name email areas categories publicFeed geoJson]} & clear-secure]
  (let [data {:name name
              :email email
              :areas (map #(if (string? %) % (:id %)) areas)
              :categories (map #(if (string? %) % (:id %)) categories)
              :publicFeed publicFeed
              :geoJson geoJson
              :userid user-id
              :subscribeEmails []}
        watch (mongo/insert-one-and-return :watches data)]
    (if clear-secure (clear-secure-data watch) watch)))

(defn update-watch [watch-id {:keys [name email areas categories publicFeed geoJson]} & clear-secure]
  (let [data {:name name
              :email email
              :areas (map #(if (string? %) % (:id %)) areas)
              :categories (map #(if (string? %) % (:id %)) categories)
              :publicFeed publicFeed
              :geoJson geoJson}
        watch (mongo/update-one-and-return :watches {:_id watch-id} {$set data})]
    (if clear-secure (clear-secure-data watch) watch)))

(defn delete-watch [watch-id]
  (mongo/delete-by-id :watches watch-id))

(defn add-subscriber [watch-id current-user email lang]
  (let [mail (if (= (:email current-user) email) nil email)
        watch (find-watch watch-id)]
    (if (some #(= (:userId %) (:_id current-user)) (:subscribeEmails watch))
      (let [new-subscribes (map #(if (= (:_id current-user) (:userId %)) (merge % {:email mail :lang lang}) %) (:subscribeEmails watch))]
        (mongo/update-one :watches
          {:_id watch-id :subscribeEmails.userId (:_id current-user)}
          {$set {:subscribeEmails new-subscribes}}))
        (mongo/update-one :watches
        {:_id watch-id}
        {$push {:subscribeEmails {:userId (name (:_id current-user)) :email mail :lang lang}}}))))

(defn remove-subscriber [watch-id user-id]
  (let [w (find-watch watch-id)
        subscribe-emails (:subscribeEmails w)]
    (mongo/update-one :watches
                      {:_id watch-id}
                      {$pull {:subscribeEmails {:userId (name user-id)}}})))

(defn is-subscribed-for [user-id watch]
  (if (nil? user-id)
    false
    (some #(= (:userId %) user-id) (:subscribeEmails watch))))
