(ns sade.i18n
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [ontodev.excel :as xls]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as enlive]
            [noir.cookies :as cookie]
            [tarkkailija.env :as env]))

(def ^:dynamic *available-langs* ["fi" "sv"])

(defn- add-term [row result lang]
  (let [k (get row "key")
        t (get row lang)]
    (if (and k t (not (s/blank? t)))
      (assoc-in result [lang k] (s/trim t))
      result)))

(defn- process-row [languages result row]
  (reduce (partial add-term row) result languages))

(defn- read-sheet [headers sheet]
  (->> sheet seq rest (map xls/read-row) (map (partial zipmap headers))))

(defn- load-excel []
  (select-keys
    (with-open [in (io/input-stream (io/resource "i18n.xlsx"))]
      (let [wb      (xls/load-workbook in)
            langs   (-> wb seq first first xls/read-row rest)
            headers (cons "key" langs)
            data    (->> wb (map (partial read-sheet headers)) (apply concat))]
        (reduce (partial process-row langs) {} data)))
    *available-langs*))

(def ^:private ^:dynamic *excel-data* (load-excel))

(defn- load-add-ons []
  (when-let [in (io/resource "i18n.txt")]
    (with-open [in (io/reader in)]
      (reduce (fn [m line]
                (if-let [[_ k v] (re-matches #"^([^:]+):\s*(.*)$" line)]
                  (assoc m (s/trim k) (s/trim v))
                  m))
              {}
              (line-seq in)))))

(defn get-localizations []
  (if (env/dev-mode?)
    ; merges i18n.txt data when in dev mode
    (assoc *excel-data* "fi" (merge (get *excel-data* "fi") (load-add-ons)))
    *excel-data*))

(defn set-available-languages [^:String langs]
  (log/infof "Setting available languages to %s" langs)
  (alter-var-root #'*available-langs* (constantly (s/split (str langs) #",")))
  ; in any case, let's reload the excel data
  (alter-var-root #'*excel-data* (constantly (load-excel))))

(defn get-terms
  "Return localization temrs for given language. If language is not supported returns terms for default language (\"fi\")"
  [lang]
  (let [terms (get-localizations)]
    (or (terms lang) (terms "fi"))))

(defn unknown-term [term]
  (if (env/dev-mode?)
    (str "???" term "???")
    (do
      (log/errorf "unknown localization term '%s'" term)
      "")))

(defn localize
  "Localize \"term\" using given \"terms\". If \"term\" is unknown, return term surrounded with triple question marks."
  [terms term]
  (or (terms term) (str "???" term "???")))

(defn localizer [lang]
  (partial localize (get-terms lang)))

(def ^:dynamic *lang* nil)
(def ^{:doc "Function that localizes provided term using the current language. Use within the \"with-lang\" block."
       :dynamic true}
  loc (fn [& _] (throw (java.lang.IllegalArgumentException. "Illegal use, call within with-lang block!"))))

(defmacro with-lang [lang & body]
  `(binding [loc (localizer ~lang)]
     ~@body))

(defn define-preferred-lang [& langs]
  (let [matching (some (set langs) (for [[k v] (get-localizations)] k))]
    (or matching "fi")))

(defn parse-accepts [request]
  (if-let [accept (get-in request [:headers "accept-language"])]
    (s/split (first (s/split accept #";")) #",")))

(defn update-lang-cookie [lang]
  (let [current (cookie/get :lang)]
    (when (not= current lang)
      (cookie/put! :lang lang))))

(defn lang-middleware [handler]
  (fn [request]
    (let [lang (or (get-in request [:params :lang])
                   (cookie/get :lang)
                   (define-preferred-lang (parse-accepts request)))]
      (binding [*lang* lang
                loc (localizer lang)]
        (update-lang-cookie lang)
        (handler request)))))

(defn current-lang []
  (cookie/get :lang "fi"))

(defn localize-enlive-content [lang]
  (fn [nodes]
    (-> nodes
      (enlive/at [[(enlive/attr? :sade-i18n)]]
        (fn [n]
          (-> n
            (update-in [:content] (constantly (with-lang lang (loc ((comp :sade-i18n :attrs) n)))))
            (update-in [:attrs] dissoc :sade-i18n)))))))
