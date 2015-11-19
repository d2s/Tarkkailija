(ns sade.security
  (:use monger.operators
        clojure.tools.logging
        hiccup.core)
  (:require [sade.mongo :as mongo]
            [sade.email :as email]
            [sade.client :as client]
            [sade.i18n :as i18n]
            [net.cgrand.enlive-html :as enlive])
  (:import [org.mindrot.jbcrypt BCrypt]))

;;
;; Internal
;;

(defn- get-hash [password salt] (BCrypt/hashpw password salt))
(defn- dispense-salt ([] (dispense-salt 10)) ([n] (BCrypt/gensalt n)))
(defn- check-password [candidate hashed] (BCrypt/checkpw candidate hashed))
(defn- generate-apikey [] (apply str (take 40 (repeatedly #(rand-int 10)))))

(defn- random-password []
  (let [ascii-codes (concat (range 48 58) (range 66 91) (range 97 123))]
    (apply str (repeatedly 40 #(char (rand-nth ascii-codes))))))

;;
;; Helpers
;;

(defn non-private
  "Returns user without private details."
  [user] (dissoc user :private))

(defn summary
  "Returns common (embeddable) information about the user or nil."
  [{:keys [_id username role] :as user}]
  (and user {:_id       _id
             :username  username
             :role      role}))

(defn user-role
  "Returns user role as keyword or nil."
  [{:keys [role]}] (keyword role))

;;
;; Api
;;

(defn login
  "Returns non-private information of first enabled user with the username and password - or nil.
   Returns nil also if username or password is nil."
  [username password]
  (when (and username password)
    (if-let [user (mongo/find-one :users {:email username})]
      (or
        (and
          (:enabled user)
          (check-password password (-> user :private :password))
          (non-private user))
        nil))))

(defn login-with-apikey
  "returns non-private information of first enabled user with the apikey"
  [apikey]
  (when apikey
    (let [user (non-private (mongo/find-one :users {:private.apikey apikey}))]
      (if (not (:enabled user))
        (warn "user " (:email user) " is :disabled, disallowing apikey authentication")
        user))))

(defn get-user-by-email [email]
  (and email (non-private (mongo/find-one :users {:email email}))))

(defn create-user [{:keys [email password role enabled] :or {role :user enabled false} :as input}]
  (infof "creating user: %s" (dissoc input :password))
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)
        id                (mongo/create-id)
        user              {:_id        id
                           :email      email
                           :role       role
                           :enabled    enabled
                           :private    {:salt salt
                                        :password hashed-password}}]
    (non-private
      (mongo/insert-one-and-return :users user))))

(defn create-user-with-apikey [{:keys [apikey email] :as input}]
  (let [u (create-user input)]
    (mongo/update-one :users {:email email} {$set {:private.apikey apikey}}))
  (mongo/find-one :users {:email email}))

(defn update-user [email data]
  (mongo/update-one :users {:email email} {$set data}))

(defn user-exists? [email]
  (not (nil? (mongo/find-one :users {:email email}))))

;; TODO: should come from a separate file
(def ^:private activation-email-default-styles
"
html {
  background-color: #f0f0f0;
}
body {
  font-family: Arial,FreeSans,Helvetica,sans-serif;
  font-size: 12pt;
  padding: 3em;
  margin: 1em;
  border: 2px solid grey;
  background-color: #ffffff;
}

h1 {
  font-size: 1.3em;
}

p {
  font-size: 1.0em;
}")

(defn prefix-string [s p]
  (if (= p (subs s 0 (count p))) s (str p s)))

; FIXME: this *really* should use the actual template!!!
; FIXME: LOCALIZE
(defn gen-activation-mail [{:keys [service-name host-url key styles lang] :or {styles activation-email-default-styles}}]
  (i18n/with-lang lang
    (let [url (str "security/activate/" key)]
      (html [:html
             [:head
              [:style {:type "text/css"} styles]]
             [:body
              [:h1 service-name]
              [:p (str (i18n/loc "email.activation.activateviaclick") " ") [:a {:href (str host-url (prefix-string url "/"))} (i18n/loc "email.activation.fromhere")]]]]))))

(defn send-activation-mail-for [{:keys [user from service-name host-url lang]}]
  (let [key     (generate-apikey)
        to      (:email user)
        subject (str service-name ": " (i18n/with-lang lang (i18n/loc "email.activation.subject")))
        message (gen-activation-mail {:service-name service-name :host-url host-url :key key :lang lang})]
    (mongo/insert-one :activation-emails {:user-id (:_id user) :activation-key key})
    (merge (email/send-mail to from subject message) {:key key})))

(defn get-activation-key [userid]
  (mongo/find-one :activation-emails {:user-id userid}))

(defn activate-account [activation-key]
  (try
    (let [act     (mongo/find-one :activation-emails {:activation-key activation-key})
          success (mongo/update-one :users {:_id (:user-id act)} {$set {:enabled true}})]
      (mongo/delete-by-id :activation-emails (:_id act))
      (when success
        (non-private (mongo/find-one :users {:_id (:user-id act)}))))
    (catch Exception e 
      (warnf e "Unable to activate account with key '%s', forcing user to frontpage" activation-key)
      (throw (ex-info "Unable to activate account" {:type :force-frontpage})))))

(enlive/deftemplate
  password-change-template
  (enlive/html-resource (clojure.java.io/resource "private/email-templates/password-reset-email.html"))
  [reset-link]
  [:#reset-link] (enlive/set-attr :href (str (client/uri) (prefix-string reset-link "/"))))

(defn send-password-change-mail-for [{:keys [user from service-name host-url reset-link-prefix]}]
  (let [key     (generate-apikey)
        to      (:email user)
        subject (str service-name ": salasanan uusiminen")
        message (apply str (password-change-template (str reset-link-prefix "/" key)))]
    (mongo/insert-one :password-reset-keys {:user-id (:_id user) :reset-key key})
    (merge (email/send-mail to from subject message) {:key key})))

(defn change-password-via-reset-key [key password]
  (let [reset-key         (mongo/find-one :password-reset-keys {:reset-key key})
        salt              (dispense-salt)
        hashed-password   (get-hash password salt)
        success           (mongo/update-one :users {:_id (:user-id reset-key)} {$set {:private.salt  salt
                                                                                      :private.password hashed-password}})]
    (when success
        (mongo/delete-by-id :password-reset-keys (:_id reset-key))
        (non-private (mongo/find-one :users {:_id (:user-id reset-key)})))))

(defn get-change-password-key [user-id]
  (mongo/find-one :password-reset-keys {:user-id user-id}))

(defn create-apikey [email]
  (let [apikey (generate-apikey)
        result (mongo/update-one :users {:email email} {$set {:private.apikey apikey}})]
    (when result apikey)))

(defn change-password [email password]
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)]
    (mongo/update-one :users {:email email} {$set {:private.salt  salt
                                                   :private.password hashed-password}})))

(defn get-or-create-user-by-email [email]
  (or
    (get-user-by-email email)
    (create-user {:email email})))

(defn create-hash-for [salt & attr]
  (BCrypt/hashpw (apply str attr) salt))
