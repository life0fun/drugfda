(ns drugfda.pdfreader.pdfreader
  (:require [clojure.string :as str])
  (:import [java.io File FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  (:import [org.apache.pdfbox.pdmodel PDDocument]
           [org.apache.pdfbox.util PDFTextStripper]
           [java.io File OutputStreamWriter FileOutputStream BufferedWriter])
  (:require [clj-redis.client :as redis])    ; bring in redis namespace
  (:require [clojure.data.json :as json]
            [clojure.java.io :only [reader writer] :refer [reader writer]])
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format :refer [parse unparse formatter]]
            [clj-time.coerce :refer [to-long from-long]]))


; this module abstract interface to extract text from pdf
;(def (second (re-find #"(?smx) \d+\.\s*SECTION\s+(.*)\d+\.\s*SECTION" s)))
; b/c the same pattern appear in the catalog and in the section, need two pattern
(def contrad-matcher #"(?smx) (?:\d+\s*CONTRAINDICATIONS(?:.*))\d+\s*CONTRAINDICATIONS(?:[\s\n ]*)(.+)\d+\s*WARNINGS\s*AND\s*PRECAUTIONS")
(def footer-matcher #"(^USPI-T-\d+|^Reference ID:)") ; "USPI-T-07331210 4 " or "Reference ID: 3209081 "
(def usage-matcher #"(?smx)(----\s*INDICATIONS\s*AND\s*USAGE\s*----(.+)(?:----\s*DOSAGE\s*AND\s*ADMINISTRATION\s*---))")
(def dose-matcher #"(?smx)(----\s*DOSAGE\s*AND\s*ADMINISTRATION\s*----(.+)(?:----\s*DOSAGE\s*FORMS\s*AND\s*STRENGTHS\s*---))")
(def doseforms-matcher #"(?smx)(----\s*DOSAGE\s*FORMS\s*AND\s*STRENGTHS\s*----(.+)(?:----\s*CONTRAINDICATIONS\s*---))")
(def contraind-matcher #"(?smx)(----\s*CONTRAINDICATIONS\s*----(.+)(?:----\s*WARNINGS\s*AND\s*PRECAUTIONS\s*---))")
(def warnings-matcher #"(?smx)(----\s*WARNINGS\s*AND\s*PRECAUTIONS\s*----(.+)(?:----\s*ADVERSE\s*REACTIONS\s*---))")
(def reactions-matcher #"(?smx)(----\s*ADVERSE\s*REACTIONS\s*----(.+)(?:----\s*DRUG\s*INTERACTIONS\s*---))")
(def interactions-matcher #"(?smx)(----\s*DRUG\s*INTERACTIONS\s*----(.+)(?:----\s*USE\s*IN\s*SPECIFIC\s*POPULATIONS\s*---))")
(def interactions-matcher #"(?smx)(----\s*USE\s*IN\s*SPECIFIC\s*POPULATIONS\s*----(.+)(?:FULL\s*PRESCRIBING\s*INFORMATION:\s*CONTENTS))")



(defn clean-text [matched-text]
  "clean up wachy text scanned from pdf optical or page footer etc"
  (letfn [(emptyline? [t]
            (= 0 (count (str/trim t))))
          (footer? [t]
            (re-find footer-matcher t))]
    (let [matchedary (str/split-lines matched-text)
          letterary (map (fn [l] (apply str (filter (fn [c] (and (>= (int c) 32) (<= (int c) 126))) l))) matchedary)
          txtary (filter #(not (or (emptyline? %) (footer? %))) letterary)]
      (doall (map prn txtary))
      txtary)))   ; retrn clean ary


(defn pdftext [pdffile outfile]
  "read passed in pdf file and return the extracted text in a seq"
  (prn "parsing file " pdffile)
  (with-open [pd (PDDocument/load (File. pdffile))
              wr (BufferedWriter. (OutputStreamWriter. (FileOutputStream. (File. outfile))))]
    (let [stripper (PDFTextStripper.)
          text (.getText stripper pd)
          usage (str " INDICATIONS AND USAGE "(last (re-find usage-matcher text)))
          ; first reg match group is the entire match string
          contrad (second (re-find contrad-matcher text))
          txtary (clean-text contrad)
          outtxt (str/join "\n", txtary)]
      (println "Number of pages" (.getNumberOfPages pd))
      ;(prn (subs contrad 0 300))
      (prn usage)
      (.write wr outtxt 0 (count outtxt)))))
      ;(.writeText stripper pd wr))))
  
