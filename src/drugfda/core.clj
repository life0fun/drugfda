(ns drugfda.core
  (:require [clojure.string :as str]
            [clojure.pprint :refer :all])
  (:import [java.io FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  ; (:require [clj-redis.client :as redis]) ; bring in redis namespace
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format]
            [clj-time.local])
  (:require [drugfda.elastic.es :as es]
            [drugfda.pdfreader.pdfreader :as pdfreader])
  (:gen-class :main true))


(def help-info (list " ---------------------------------"
                     "lein run parse pdf-file out-file"
                     "lein run create-drug-index"
                     "lein run contraindication drug-name keyword"
                     "lein run usage drug-name"
                     "lein run dosage drug-name"
                     "lein run precaution drug-name"
                     "lein run reaction drug-name"
                     "lein run interaction drug-name"
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
  (case (first args)
    "help" (doall (map prn help-info))
    "parse" (parse-pdf (second args) (last args))
    "create-drug-index" (es/create-drug-index)
    "contraindication" (search-prescribing-info (second args) "contraindication" (last args))
    "usage" (search-prescribing-info (second args) "usage" (last args))
    "precaution" (search-prescribing-info (second args) "precaution" (last args))
    "interaction" (search-prescribing-info (second args) "interaction" (last args))
    ;(parse-pdf "./doc/simvastatin.pdf" "./x")
    (doall (map prn help-info))))  ; default