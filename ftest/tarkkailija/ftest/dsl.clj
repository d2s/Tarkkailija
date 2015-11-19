(ns tarkkailija.ftest.dsl
  (:use clj-webdriver.taxi
        tarkkailija.iftest.util
        clojure.stacktrace
        sade.client))

;;
;; Common
;;

(defn clear-input-text [id]
  (input-text id (apply str (repeat 128 \backspace))))

(defn autocomplete-select
  ([id text] (autocomplete-select id text 0))
  ([id text sel-ind]
    (input-text id text)
    (wait-until (exists? ".ui-autocomplete a"))
    (click (nth (elements ".ui-autocomplete a") sel-ind))))

(defn exists-and-visible? [q]
  (if-let [e (element *driver* q)]
    (and (exists? e) (displayed? e))))

(defn text-as-int [s] (-> s text read-string))

(defn wait-until-text-is
  "Makes browser wait until the provided element's text is equal to the string representation of
   provided value. Returns true if this happened before timeout, false otherwise"
  ([e v] (wait-until-text-is e v 3000))
  ([e v timeout]
  (try
    (do
      (wait-until #(and (exists-and-visible? e) (= (text e) (str v)) timeout))
      true)
    (catch Exception e
      false))))

(defn wait-until-enabled
  "Makes browser wait until the provided input element is in enabled state. Returns true if this
   happened before timeout, otherwise returns false."
  ([e] (wait-until-enabled e 3000))
  ([e timeout]
    (try
      (do
        (wait-until #(and (exists-and-visible? e) (enabled? e)) timeout)
        true)
      (catch Exception e
        false))))

(defn click-with-wait
  "Same as clj-webdriver's default click function but waits for defined time for the element to show.
   If the element does not show up before the specified time has passed an exception is thrown (not NPE like with
   normal click function!)"
  ([e] (click-with-wait e 3000))
  ([e timeout]
    (try
      (do
        (wait-until #(exists-and-visible? e) timeout)
        (click e))
      (catch Exception npe
        (throw (java.lang.RuntimeException. (str "No such element '" e "' found within timeout period of " timeout " ms, could not click it")))))))

;;
;; Project
;;

(def timo {:username "timo@solita.fi" :password "timo" :firstname "Timo" :lastname "Testaaja" :apikey "5087ba34c2e667024fbd5992"})
(def simo {:username "simo@solita.fi" :password "simo" :firstname "Simo" :lastname "Testaaja" :apikey "5087ba34c2e667024fbd5993"})

(defn ->frontpage [] (to (uri)))
(defn ->searchpage
  ([] (to (uri "#/search")))
  ([watch-id] (to (uri "#/search/" watch-id))))

(defn create-user [user]
  (json-get (uri "/dev/api/fixture/create-activated-user") user))

(defn logout []
  (when (exists-and-visible? "#logout")
    (click "#logout")))

(defn login [{:keys [username password dont-wait]}]
  (->frontpage)
  (click "#login-show")
  ;(wait-until #(displayed? "#login-form"))
  (input-text "#username-login" username)
  (input-text "#password-login" password)
  (click "#login-submit")
  (when (or (false? dont-wait) (nil? dont-wait))
    (wait-until #(displayed? "#login-info"))))

(defmacro as-user [user & body]
  `(do
     (create-user ~user)
     (login ~user)
     (try (do ~@body) (catch Exception ~'e (print-cause-trace ~'e)))
     (logout)))

; FIXME: this cannot work in the real use scenario, one has to create a new feed
(defn search-something []
  (to (uri "#/search?areas=Helsinki&categories=Asuminen"))
  (-> ".result" exists? wait-until))
