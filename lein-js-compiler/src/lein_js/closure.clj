(ns lein-js.closure
  (:require [clojure.java.io :as io])
  (:import [com.google.javascript.jscomp CompilerOptions JSSourceFile
	    CompilationLevel WarningLevel ClosureCodingConvention
	    DiagnosticGroups CheckLevel]
	   [java.nio.charset Charset]))

(def compile-levels {:whitespace CompilationLevel/WHITESPACE_ONLY 
                     :simple     CompilationLevel/SIMPLE_OPTIMIZATIONS
                     :advanced   CompilationLevel/ADVANCED_OPTIMIZATIONS})

(defn compiler-options [compile-level]
  (let [opt (CompilerOptions.)]
    (.setOptionsForCompilationLevel ((keyword compile-level) compile-levels (:whitespace compile-levels)) opt)
    (.setWarningLevel opt DiagnosticGroups/NON_STANDARD_JSDOC CheckLevel/OFF)
    opt))

(defn compile-js [compile-level output-file js-files]
  (let [compiler (com.google.javascript.jscomp.Compiler. System/err)
        files    (map #(JSSourceFile/fromFile % (Charset/forName "UTF-8")) js-files)]
    (io/make-parents output-file)
    (com.google.javascript.jscomp.Compiler/setLoggingLevel java.util.logging.Level/WARNING)
    (doto compiler
      (.compile (into-array JSSourceFile []) ; externs, not needed
                (into-array JSSourceFile files)
                (compiler-options compile-level)))
    (spit output-file (.toSource compiler))
    (println "Compiled a bundled JavaScript into" output-file)))