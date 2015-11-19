(ns leiningen.js-compile-test
  (:use midje.sweet)
  (:require [leiningen.js-compile :as js]
            [lein-js.closure :as closure]
            [net.cgrand.enlive-html :as enlive]))

(def test-html
  "<html>
    <head>
      <script type=\"text/javascript\" src=\"foo.js\"></script>
      <script type=\"text/javascript\" src=\"path/bar.js\"></script>
    </head>
    <body>
      <p>Also script elements from body should be found and handled</p>
      <script type=\"text/javascript\" src=\"lib/bloo.js\"></script>
      <script type=\"application/javascript\" src=\"another.js\"></script>
      <p>But script elements without javascript type shouldn't match</p>
      <script type=\"application/dart\" src=\"foo.dart\"></script>
    </body>
   </html>")

(fact "Javascript resource paths are correctly parsed from HTML"
  (js/locate-resources-from-html test-html) => (just #{"foo.js" "path/bar.js" "lib/bloo.js" "another.js"}))

(fact "Resource paths given with :files are interpreted as is"
  (js/locate-resources {:files ["foo.js" "bar.js"]}) => (just #{"foo.js" "bar.js"}))

(fact "Resource paths from both :html and :files are combined together"
  (js/locate-resources {:html ["foo.html"] :files ["a.js" "b.js"]}) => (just #{"foo.js" "path/bar.js" "lib/bloo.js" "another.js" "a.js" "b.js"})
  (provided (js/load-html-file "foo.html") => test-html))

(fact "Replacing HTML has all compiled references removed and has a new all.js reference"
  (defchecker expected-html? [h]   
    (let [scripts (map #(-> (enlive/attr-values % :src) first) (enlive/select h [[:script]]))]
      scripts => (just #{"all.js" "foo.dart"})))
  
  (js/js-compile {:root ""
                  :compile-path ""
                  :js-resources {:html ["foo.html"]
                                 :html-output "result.html"
                                 :output "all.js"
                                 :prefix ""}}) => nil
  (provided 
    (js/load-html-file "foo.html") => test-html
    (closure/compile-js anything anything anything) => nil
    ;; FIXME: this is so ugly, but got bored to get midje mocks to return exactly what I want
    (js/convert-to-files anything anything anything) => (map clojure.java.io/file ["foo.js" "path/bar.js" "lib/bloo.js" "another.js"])
    (js/write-html-file "/resources/result.html" expected-html?) => nil))