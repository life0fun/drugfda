(ns drugfda.core
  (:require [clojure.string :as str])
  (:import [java.io FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  ; (:require [clj-redis.client :as redis]) ; bring in redis namespace
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format]
            [clj-time.local])
  (:require [drugfda.elastic.es :as es]
            [drugfda.pdfreader.pdfreader :as pdfreader])
  (:gen-class :main true))


(def help-info (list " -------------------------"
                     "lein run parse pdf-file out-file"
                     "lein run create-drug-index"
                     "lein run contraindication [usage dosage contraindication precaution reaction interaction] drugname keyword"
                ))

; convert a pdf file
(defn parse-pdf [pdffile outfile]
  "parse a pdf file, extract the prescribing highlight info sections and insert into ES"
  (pdfreader/pdftext pdffile outfile))


; search drug prescribing info
(defn search-prescribing-info
  "search drug prescribing highlight info"
  [drugname section qword]
  (es/search drugname section qword))


; the main 
(defn -main [& args]
  (doall (map prn help-info))
  (case (first args)
    "parse" (parse-pdf (second args) (last args))
    "create-drug-index" (es/create-drug-index)
    "contraindication" (search-prescribing-info (second args) "contraindication" (last args))
    (parse-pdf "./doc/simvastatin.pdf" "./x")))