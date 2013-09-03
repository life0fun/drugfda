(ns drugfda.pdfreader.pdfreader
  (:require [clojure.string :as str])  ; clojure str already part of
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
            [clj-time.coerce :refer [to-long from-long]])
  (:require [drugfda.elastic.es :refer :all]))


; this module abstract interface to extract text from pdf
;(def (second (re-find #"(?smx) \d+\.\s*SECTION\s+(.*)\d+\.\s*SECTION" s)))
; b/c the same pattern appear in the catalog and in the section, need two pattern
(def contrad-matcher #"(?smx) (?:\d+\s*CONTRAINDICATIONS(?:.*))\d+\s*CONTRAINDICATIONS(?:[\s\n ]*)(.+)\d+\s*WARNINGS\s*AND\s*PRECAUTIONS")
(def footer-matcher #"(^USPI-T-\d+|^Reference ID:)") ; "USPI-T-07331210 4 " or "Reference ID: 3209081 "
; matcher for various sections in the first page.
(def usage-matcher #"(?smx)(----\s*INDICATIONS\s*AND\s*USAGE\s*----(.+)(?:----\s*DOSAGE\s*AND\s*ADMINISTRATION\s*---))")
(def dosage-matcher #"(?smx)(----\s*DOSAGE\s*AND\s*ADMINISTRATION\s*----(.+)(?:----\s*DOSAGE\s*FORMS\s*AND\s*STRENGTHS\s*---))")
(def doseforms-matcher #"(?smx)(----\s*DOSAGE\s*FORMS\s*AND\s*STRENGTHS\s*----(.+)(?:----\s*CONTRAINDICATIONS\s*---))")
(def contraind-matcher #"(?smx)(----\s*CONTRAINDICATIONS\s*----(.+)(?:----\s*WARNINGS\s*AND\s*PRECAUTIONS\s*---))")
(def warnings-matcher #"(?smx)(----\s*WARNINGS\s*AND\s*PRECAUTIONS\s*----(.+)(?:----\s*ADVERSE\s*REACTIONS\s*---))")
(def reactions-matcher #"(?smx)(----\s*ADVERSE\s*REACTIONS\s*----(.+)(?:----\s*DRUG\s*INTERACTIONS\s*---))")
(def interactions-matcher #"(?smx)(----\s*DRUG\s*INTERACTIONS\s*----(.+)(?:----\s*USE\s*IN\s*SPECIFIC\s*POPULATIONS\s*---))")
(def populations-matcher #"(?smx)(----\s*USE\s*IN\s*SPECIFIC\s*POPULATIONS\s*----(.+)(?:FULL\s*PRESCRIBING\s*INFORMATION:\s*CONTENTS))")

; match the long dash line from pdf, not using ^--+, but think more than 3 dash needs to be cleaned.
(def leading-dash-matcher #"(?smx)(----+[\s\n ]*)")

(defn write-to-file
  "append a list of paragraphs json to an output file, use pr-str to convert json to string"
  [ofile & paras]
  (with-open [wr (writer ofile :append true)]
    (doall (map #(.write wr (pr-str %)) paras))))  ; eval lazy seq with doall 


; deprecated, already integrated into clean-text
(defn trim-head-tail
  "trim the leading and trailing ----\n symbols"
  [txt]
  (let [trim-dash (str/replace txt leading-dash-matcher "")
        trim-newline (str/replace trim-dash #"[\n\t]" " ")]
    trim-newline))


(defn clean-text [matched-text]
  "clean up wacky text scanned from pdf optical or page footer etc"
  (letfn [(emptyline? [t]
            (= 0 (count (str/trim t))))
          (footer? [t]
            (re-find footer-matcher t))]
    (let [matchedary (str/split-lines matched-text)
          letterary (map (fn [l] (apply str (filter (fn [c] (and (>= (int c) 32) (<= (int c) 126))) l))) matchedary)
          txtary (filter #(not (or (emptyline? %) (footer? %))) letterary)
          txtblock (str/join " " txtary)
          trim-dash (str/replace txtblock leading-dash-matcher "")
          trim-newline (str/replace trim-dash #"[\n\t]" " ")]
      trim-newline)))   ; retrn clean ary


(defn pdftext [pdffile outfile]
  "read pdf file, extract prescribing hightlight sections and insert extracted sections into ES drug index"
  (prn "parsing file " pdffile)
  (with-open [pd (PDDocument/load (File. pdffile))
              wr (BufferedWriter. (OutputStreamWriter. (FileOutputStream. (File. outfile))))]
    (let [stripper (PDFTextStripper.)
          text (.getText stripper pd)
          usage (hash-map (keyword (nth sections 1)) 
                          (clean-text (last (re-find usage-matcher text))))
          dosage (hash-map (keyword (nth sections 2)) 
                           (clean-text (last (re-find dosage-matcher text))))
          contrainds (hash-map (keyword (nth sections 3)) 
                               (clean-text (last (re-find contraind-matcher text))))
          precautions (hash-map (keyword (nth sections 4)) 
                                (clean-text (last (re-find warnings-matcher text))))
          reactions (hash-map (keyword (nth sections 5)) 
                              (clean-text (last (re-find reactions-matcher text))))
          interactions (hash-map (keyword (nth sections 6)) 
                                 (clean-text (last (re-find interactions-matcher text))))
          populations (hash-map :populations 
                                (clean-text (last (re-find populations-matcher text))))
          ; first reg match group is the entire match string
          contrad-section (clean-text (second (re-find contrad-matcher text)))]
      ;(prn " >>>> " (clean-text (first (vals reactions))))
      ;(prn " ... " contrad-section)
      ;(write-to-file outfile usage dosage contrainds precautions reactions interactions populations outtxt)
      ; now insert info json sections into es engine
      (insert-drug-doc "zocor" usage dosage contrainds precautions reactions interactions)
      (println "Number of pages" (.getNumberOfPages pd)))))
      ;(.write wr outtxt 0 (count outtxt)))))
      ;(.writeText stripper pd wr))))
  
