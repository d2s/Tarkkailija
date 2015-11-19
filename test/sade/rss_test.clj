(ns sade.rss-test
  (:use clojure.test
        midje.sweet
        sade.rss))

(facts
  (generate-rss {:title "otsikko" :link "linkki" :description "kuvaus"}
                [{:title "artikkelin otsikko" :link "artikkelin linkki" :description "artikkelin kuvaus"}
                 {:title "toisen artikkelin otsikko" :link "toisen artikkelin linkki" :description "toisen artikkelin kuvaus"}]) => "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\" xmlns:georss=\"http://www.georss.org/georss\"><channel><link>linkki</link><title>otsikko</title><description>kuvaus</description><item><link>artikkelin linkki</link><title>artikkelin otsikko</title><description>artikkelin kuvaus</description></item><item><link>toisen artikkelin linkki</link><title>toisen artikkelin otsikko</title><description>toisen artikkelin kuvaus</description></item></channel></rss>")
