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
(def *contrad-matcher* #"(?smx) (?:\d+\s*CONTRAINDICATIONS(?:.*))\d+\s*CONTRAINDICATIONS(?:[\s\n ]*)(.+)\d+\s*WARNINGS\s*AND\s*PRECAUTIONS")


(defn clean-text [matched-text]
  "clean up wachy text scanned from pdf optical or page footer etc"
  (let [txtary (str/split-lines matched-text)
        cleanary (map (fn [l] (apply str (filter (fn [c] (and (>= (int c) 32) (<= (int c) 126))) l))) txtary)]
    (doall (map prn cleanary))
    cleanary))   ; retrn clean ary


(defn pdftext [pdffile outfile]
  "read passed in pdf file and return the extracted text in a seq"
  (prn "parsing file " pdffile)
  (with-open [pd (PDDocument/load (File. pdffile))
              wr (BufferedWriter. (OutputStreamWriter. (FileOutputStream. (File. outfile))))]
    (let [stripper (PDFTextStripper.)
          text (.getText stripper pd)
          ; first reg match group is the entire match string
          contrad (second (re-find *contrad-matcher* text))
          txtary (clean-text contrad)]
      (println "Number of pages" (.getNumberOfPages pd))
      (prn "size : " (count text))
      ;(prn (subs contrad 0 300))
      (.write wr contrad 0 (count contrad)))))
      ;(.writeText stripper pd wr))))
  
