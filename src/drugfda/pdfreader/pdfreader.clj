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

(defn pdftext [pdffile]
  "read passed in pdf file and return the extracted text in a seq"
  (prn "parsing file " pdffile)
  (with-open [pd (PDDocument/load (File. pdffile))
              dest "./x"
              wr (BufferedWriter. (OutputStreamWriter. (FileOutputStream. (File. dest))))]
    (let [stripper (PDFTextStripper.)
          text (.getText stripper pd)]
      (println "Number of pages" (.getNumberOfPages pd))
      (prn "size : " (count text)))))
      ;(.writeText stripper pd wr))))
  
