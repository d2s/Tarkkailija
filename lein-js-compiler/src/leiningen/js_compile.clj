(ns leiningen.js-compile
  (:require [lein-js.closure :as closure]
            [net.cgrand.enlive-html :as enlive]))

(defn take-first-when [pred col]
  (first (filter pred col)))

(def not-nil? (complement nil?))

(defn remove-last [s]
  (apply str (drop-last s)))

(def add-slashes 
  (comp
    #(if (.endsWith % "/") % (str % "/"))
    #(if (.startsWith % "/") (.substring % 1) %)))

(defn build-prefix [root prefix]
  (remove-last
    (apply 
      str
      (map
        add-slashes
        (filter not-empty ["resources" root prefix])))))

(defn load-html-file [html-path]
  (slurp (clojure.java.io/file html-path)))

(defn create-matchers [ex]
  (map (fn [r] (fn [s] (re-matches r s))) (or ex [#"$^"])))

(defn locate-resources-from-html-fn [^clojure.lang.LazySeq html exclude]
  (remove 
    #((apply some-fn (create-matchers (first exclude))) %) 
    (map 
      (comp :src :attrs)
      (concat 
        (enlive/select html [[:script (enlive/attr= :type "text/javascript")]])
        (enlive/select html [[:script (enlive/attr= :type "application/javascript")]])))))

(defmulti locate-resources-from-html (fn [a & b] (class a)))
(defmethod locate-resources-from-html String [html & exclude]
  (locate-resources-from-html-fn (enlive/html-resource (java.io.StringReader. html)) (or exclude [])))
(defmethod locate-resources-from-html clojure.lang.LazySeq [html & exclude]
  (locate-resources-from-html-fn html (or exclude [])))

(defn locate-resources [res]
  (apply concat 
    (:files res)
    (map (comp #(locate-resources-from-html % (:exclude res)) 
               load-html-file) 
         (:html res))))

(defn convert-to-res-file [res res-dirs suffix]
  (take-first-when not-nil?
    (map 
      #(let [f (clojure.java.io/file % suffix res)] (if (.exists f) f nil)) 
      res-dirs)))

(defn convert-to-files [resources res-dirs suffix]
  (map #(convert-to-res-file % res-dirs suffix) resources))

(defn build-selectors-for-js-files [files]
  (map (fn [f] [:script (enlive/attr= :src f)]) files))

(defn clear-matching-script-elements [html res]
  (map 
    (fn [s] (fn [d] (enlive/at d [s] (constantly nil))))
    (build-selectors-for-js-files (locate-resources-from-html html (:exclude res)))))

(defn append-all-script-element [src]
  (fn [d] 
    (enlive/at 
      d 
      [:body] 
      (enlive/append 
        (enlive/html-snippet 
          (format "<script type=\"text/javascript\" src=\"%s\"></script>" src))))))

(defn convert-html-resources [res]
  ; FIXME: kinda ugly to just pick the first one
  (let [html (enlive/html-resource (java.io.StringReader. (first (map load-html-file (:html res)))))]
    (reduce 
      (fn [d f] (f d)) 
      html
      (conj
        (clear-matching-script-elements html res)
        (append-all-script-element (clojure.string/replace (:output res) (:prefix res) ""))))))

(defn target-dir [& args]
  (remove-last (apply str (map add-slashes args))))

(defn ensure-absolute [f]
  (let [file (clojure.java.io/file f)]
    (if (.isAbsolute file)
      f
      (str "/" f)))) ; FIXME: yeah, I know. Ugly. Quick hack for now

(defn write-html-file [f content]
  (let [absolute (ensure-absolute f)]
    (clojure.java.io/make-parents absolute)
    (spit (clojure.java.io/file absolute) (apply str (enlive/emit* content)))
    (println "Wrote" absolute)))

(defn convert-html [root res]
  (when (:html-output res)
    (printf "Converting %s to include only compiled JavaScript resources\n" (clojure.string/join ", " (:html res)))
    (write-html-file (target-dir root "resources" (:html-output res)) (convert-html-resources res))))

(defn js-compile
  "Compiles your Javascript codes with Google Closure Compiler"
  [{:keys [js-resources] :as project} & args]
  (println (:resource-paths project))
  (let [resources (convert-to-files 
                    (locate-resources js-resources)
                    (:resource-paths project)
                    (:prefix js-resources))]
    
    (println "Compiling JavaScript resources:\n " 
             (clojure.string/join "\n  " (map #(.getAbsolutePath %) resources)))
    
    (closure/compile-js
      (:compile-level js-resources)
      (str (:compile-path project) "/" (build-prefix (:output js-resources) nil))
      resources)
    
    (convert-html (:compile-path project) js-resources)))