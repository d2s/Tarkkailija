(ns tarkkailija.feedback
  (:require [sade.security :as security]
            [sade.email :as email]
            [tarkkailija.env :as env]
            [hiccup.core :as hiccup]
            [clojure.tools.logging :as log]))

(def ^:dynamic ^:String *feedback-email-target-address* (env/property "feedback.target.email" nil))

(defn set-email-target-address [email]
  (log/infof "Setting feedback email target address to %s" email)
  (alter-var-root #'*feedback-email-target-address* (constantly (clojure.string/split email #","))))

(defn gen-feedback-mail [{:keys [service-name host-url key feedback styles] :or {styles ""}}]
  (let [modified-text (clojure.string/replace (hiccup/h (:text feedback)) #"\n" "<br>")]
    (hiccup/html
      [:html
       [:head
        [:base {:href host-url}]
        [:style {:type "text/css"} styles]]
       [:body
        [:h1 service-name " palaute"]
        [:p
         "_UUID:_"[:br] (:uuid feedback) [:br][:br]
         "_Type:_"[:br] (:type feedback) [:br][:br]
         "_Reply_Email:_"[:br] (or (:reply-to feedback) "<none given>") [:br][:br]
         "_User ID:_"[:br] (if (:userid feedback) (:userid feedback) "Not logged user") [:br][:br]
         "_Remote address:_" [:br] (:remote-addr feedback) [:br][:br]
         "_User-agent:_"[:br] (:user-agent feedback) [:br][:br]
         "_Timestamp:_"[:br] (:timestamp feedback) [:br][:br]
         "_Text:_"[:br] modified-text]
        ]])))

(defn send-feedback-mail-for [{:keys [from service-name host-url feedback]}]
  (if *feedback-email-target-address*
    (let [key     (str (java.util.UUID/randomUUID))
          to      *feedback-email-target-address*
          subject (str service-name ": Palaute, id: " (:uuid feedback) ", type: " (:type feedback))
          message (gen-feedback-mail {:service-name service-name
                                      :host-url host-url
                                      :key key
                                      :feedback feedback})]
      (log/infof "Received feedback, sending it to target address with id %s" key)
      (merge (email/send-mail to from subject message) {:key key}))
    (log/infof "Received feedback, NOT sending it because target address is not set")))
