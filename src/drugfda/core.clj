(ns drugfda.core
  (:require [clojure.string :as str])
  (:import [java.io FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  (:require [clj-redis.client :as redis]) ; bring in redis namespace
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format]
            [clj-time.local])
  (:require [drugfda.elastic.es :as es]
            [drugfda.pdfreader.pdfreader :as pdfreader])
  (:gen-class :main true))


; convert a pdf file
(defn parse-pdf [pdffile outfile]
  "parse a pdf file and return a text sequence"
  (pdfreader/pdftext pdffile outfile))


; the main 
(defn -main [& args]
  (prn " >>>> starting <<<<< ")
  (case (first args)
    "parse" (parse-pdf (rest args))
    (parse-pdf "./doc/simvastatin.pdf" "./x")))