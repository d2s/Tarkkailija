(ns sade.email
  (:require [postal.core :as postal]
            [clojure.tools.logging :as log]
            [tarkkailija.env :as env]))

(def ^:dynamic ^:Boolean enabled true)
(def ^:dynamic smtp-settings (into {} (for [[a b] (filter val {:host (env/property "email.host" nil)
                                                               :ssl  (env/property "email.ssl" nil)
                                                               :user (env/property "email.user" nil)
                                                               :pass (env/property "email.password" nil)})]  [a (name b)])))
(def ^:dynamic default-from "no-reply@etarkkailija.fi")

(defn set-smtp-settings! [settings]
  (when-let [new-settings (seq (for [[a b] (filter val settings)]  [a (name b)]))]
    (log/info "Setting email configurations as " (into {} new-settings))
    (alter-var-root #'smtp-settings (constantly (into {} new-settings)))))

(defn disable-sending! []
  (log/warn "Disabling email sending, all emails will be lost!")
  (alter-var-root #'enabled (constantly false)))

(defn enable-sending! []
  (log/info "Enabling email sending, further emails will be sent to their recipients!")
  (alter-var-root #'enabled (constantly true)))

(defn sent? [{result :result}] (= :sent result))

(defn enabled? [] enabled)

(defn send-mail
  "Sends HTML email and returns a map with :result key representing the success or failure"
  [to from subject html-msg]
  (if (enabled?)
    (let [result (postal/send-message smtp-settings
                                     {:user-agent "SaDe Mailer"
                                      :from (or from default-from)
                                      :to to
                                      :subject subject
                                      :body [{:type "text/html; charset=utf-8"
                                              :content html-msg}]})]
      (if (= (:error result) :SUCCESS)
        {:result :sent}
        {:result :error, :reason (:msg result)}))
    {:result :sending-disabled}))
