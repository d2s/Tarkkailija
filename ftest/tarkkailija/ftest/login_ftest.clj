(ns tarkkailija.ftest.login_ftest
  (:use clojure.test
        clj-webdriver.taxi
        midje.sweet
        sade.client
        tarkkailija.ftest.dsl
        tarkkailija.iftest.util))

(defn- get-activation-uri [email]
  (let [response       (json-get (uri "/dev/api/fixture/activation-url/" email))]
    (:activation-url response)))

(defn- get-change-password-uri [email]
  (let [response       (json-get (uri "/dev/api/fixture/change-password-url/" email))]
    (:change-password-url response)))

(with-server-and-browser
  (let [email     "teemu.testaaja@solita.fi"
        password  "U6H8EITb7X52swO"]

    (clear-db!)
    (logout)
    (->frontpage)

    (fact "user is not logged in"
      (->frontpage)
      (displayed? "#login-info") => falsey
      (displayed? "#login-show") => truthy)

    (fact "user registers via form"
      (click "#register-link")

      (input-text "#create-new-user [name='email']" email)
      (input-text "#create-new-user [name='password']" password)
      (input-text "#create-new-user [name='password2']" password)
      (click "#acceptTerms")
      (click "#save-new-user")

      (wait-until #(displayed? "#activation-email"))
      (displayed? "#activation-email") => true)

    (fact "user clicks activation email and gets logged in"
      (to (get-activation-uri email))
      (wait-until #(displayed? "#login-info"))
      (displayed? "#login-info") => truthy)

    (fact "user can logout"
      (click "#logout")

      (wait-until #(displayed? "#login-show"))
      (displayed? "#login-info") => falsey)

    (fact "user can login"
      (login {:username email :password password})
      (displayed? "#login-info") => truthy)

    (fact "user can change his forgotten password"
      (let [new-pwd ".-hubbaBubba15-."]
        (logout)

        ; try logging in with false credentials, move to "forgot password" page
        (login {:username email :password "foobar" :dont-wait true})
        (click-with-wait "#forgot-password-link")

        ; type the new email address
        (input-text "#reset-password [name=\"email\"]" (str email))
        (click "#request-reset-mail")
        (wait-until #(displayed? ".info"))

        ; go to change password page
        (to (get-change-password-uri email))
        (wait-until #(displayed? "#change-password"))

        ; type the new password in
        (input-text "#change-password input[name=\"password\"]" new-pwd)
        (input-text "#change-password input[name=\"password2\"]" (str new-pwd "\n"))
        (wait-until #(displayed? ".info"))

        ; finally, try to login with the new password
        (login {:username email :password new-pwd})
        (displayed? "#login-info") => truthy))))
