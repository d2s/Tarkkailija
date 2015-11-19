(ns tarkkailija.ftest.wizard-ftest
  (:use [clj-webdriver.taxi]
        [midje.sweet]
        [tarkkailija.ftest.dsl]
        [tarkkailija.iftest.util])
  (:require [sade.client :as client]
            [tarkkailija.leiki :as leiki]))

(defn fill-frontpage-area-input [s] (autocomplete-select ".wizard-box input" s))
(def frontpage-wizard-first-section "#frontpage-wizard-area-input")
(def frontpage-wizard-second-section "#frontpage-wizard-category-input")
(def frontpage-wizard-third-section "#frontpage-wizard-continue")
(def frontpage-wizard-categories ".category-tags li")
(def result-page-articles ".content article")
(def public-watch-button ".search-options .radio-options input[type=\"radio\"][value=\"public\"]")
(def watch-name "#watch-name")

(defn select-categories [& categories]
  (wait-until (visible? frontpage-wizard-categories))
  (doseq [c (filter #((set categories) ((comp :fi :names) %)) (leiki/frontpage-categories))]
      (click (str "#wizard-category-" (:id c)))))

(defn fill-frontpage-wizard [{areas :areas categories :categories}]
  (doseq [a areas] (fill-frontpage-area-input a))
  (click frontpage-wizard-second-section)
  (apply select-categories categories))

(defn save-watch-as [name]
  (click "#save-watch")
  (input-text "#watch-name-input" (str name "\n"))
  (wait-until-enabled "#save-watch")
  (click "#watch-recreate-confirm"))

(defn current-watch-id []
  (re-find #"(?<=/)[a-z0-9]+\Z" (current-url)))

(defn wait-until-watch-url [pred]
  (wait-until #(pred (current-watch-id))))


; and from here starts the actual tests :)
(with-server-and-browser

  (fact "User can apply watch settings without logging in"
    (clear-db!)
    (->frontpage)
    (fill-frontpage-area-input "Jokioinen\n")
    (click frontpage-wizard-second-section)
    (select-categories "Ympäristö ja luonto")
    (click frontpage-wizard-third-section)

    (wait-until-enabled result-page-articles 10000) = true

    (count (css-finder result-page-articles)) => #(> % 0))

  (facts "Watch save and delete"
    (clear-db!)

    (as-user simo
      (fact "Logged user can save a watch"
        (->frontpage)
        (fill-frontpage-wizard {:areas ["Jokioinen"] :categories ["Turvallisuus" "Liikunta ja urheilu"]})
        (click frontpage-wizard-third-section)

        (wait-until-enabled result-page-articles 10000) => true
        (save-watch-as "Simon vahti")

        (wait-until #(displayed? "#subscriptionSelectionPane") 10000)

        (current-watch-id) => #(not (= "search" %)))

      (fact "Deleted watch is really removed"
        (let [watch-id (current-watch-id)]
          (Thread/sleep 1000)
          (click "#save-delete")
          (wait-until #(displayed? "#watch-delete-confirm"))
          (click "#watch-delete-confirm")

          (wait-until #(not (displayed? "#subscriptionSelectionPane")) 10000)

          (:status (client/plain-get (client/uri (str "/api/watches/" watch-id)) (:apikey simo))) => 404))))

  (fact "More articles can be fetched"
    (->frontpage)
    (fill-frontpage-wizard {:areas ["Jokioinen"] :categories ["Turvallisuus" "Liikunta ja urheilu"]})
    (click frontpage-wizard-third-section)

    (wait-until-enabled result-page-articles 10000) => true
    (let [orig-article-count (count (elements "article.result"))]
      (click-with-wait "#moreArticlesButton")
      (wait-until-enabled "#moreArticlesButton" 10000) => true
      (count (elements "article.result")) => (+ 20 orig-article-count)))

  (fact "Public feeds can be seen on search page without login"
    (clear-db!)

    (as-user simo
      (->frontpage)
      (fill-frontpage-wizard {:areas ["Jokioinen"] :categories ["Turvallisuus" "Liikunta ja urheilu"]})
      (click frontpage-wizard-third-section)

      (click public-watch-button)
      (save-watch-as "Public watch")
      (wait-until-watch-url #(not (= "search" %)))

      (let [watch-id (current-watch-id)]
        (logout)

        (->searchpage watch-id)
        (wait-until-watch-url #(not (= "search" %)))

        (wait-until-text-is watch-name "Public watch") => true))))
