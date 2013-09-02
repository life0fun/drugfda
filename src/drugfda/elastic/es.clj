(ns drugfda.elastic.es
  (:require [clojure.string :as str])
  (:require [clojure.java.jdbc :as sql])
  (:import [java.io FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  (:require [clj-redis.client :as redis])    ; bring in redis namespace
  (:require [clojure.data.json :as json]
            [clojure.java.io :only [reader writer] :refer [reader writer]])
  (:require [clojurewerkz.elastisch.rest          :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.index    :as esi]
            [clojurewerkz.elastisch.query         :as q]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [clojure.pprint :as pp])
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format :refer [parse unparse formatter]]
            [clj-time.coerce :refer [to-long from-long]]))

; ES doc format: every doc in ES has [] _id, _index, _type, _score, _source.]
; the _source column is where all custom mapping columns are defined. for logstash,
; @source, @tags, @type, @message, @timestamp, @timestamp, ...

; each index(db) has a list of mappings types(tables) that maps doc fields and their core types.
; each mapping has a name and :properties column family, where all columns and col :type and attrs.
; mapping-type {"person" {:properties {:username {:type "string" :store "yes"} :age {}}}}
;               "vip"    {:properties {:vipname {:type "multi_field" :store "yes"} :age {}}}
; column :type "multi_field" allows a list of core_types to apply to the same column during mapping.
;"properties" : {
;   "name" : {
;     "type" : "multi_field",
;     "path": "just_name",  // how multi-field is accessed, apart from the default field
;     "fields" : {
;         "name" : {"type" : "string", "index" : "analyzed"},
;         "untouched" : {"type" : "string", "index" : "not_analyzed"}}

; overall query object format
; {
;     size: # number of results to return (defaults to 10)
;     from: # offset into results (defaults to 0)
;     fields: # list projected fields that should be returned - http://elasticsearch.org/guide/reference/api/search/fields.html
;     sort: # define sort order - see http://elasticsearch.org/guide/reference/api/search/sort.html
;     query: {
;         query_string: { fields: [] query: "query term"}
;     },
;     facets: {
;         # Facets provide summary information about a particular field or fields in the data
;     }
;     # special case for situations where you want to apply filter/query to results but *not* to facets
;     filter: {
;         # filter objects
;         # a filter is a simple "filter" (query) on a specific field.
;         # Simple means e.g. checking against a specific value or range of values
;     },
; }
;
; example query object: {
;  "size": 100, 
;  "fields": ["@tags", "@type", "@message"]
;  "query": {
;     "filtered":{  <= a json obj contains a query and a filter, apply filter to the query result
;       "query":{ 
;         "query_string":{
;           "fields": ["@tags", "@type", "column-name.*"], <= for * match, same name fields must have same type.
;           "default_operator": "OR",
;           "default_field": "@message",
;           "query": " keyword AND @type:finder_core_",
;           "use_dis_max": true}}  <= convert query into a DisMax query
;       "filter": {
;         "range": {
;           "@timestamp": {
;             "from": "2013-05-22T16:10:48Z", "to": "2013-05-23T02:10:48Z"}}}}},
;   "from": 0,
;   "sort": {
;     "@timestamp":{
;       "order": "desc"}},

; For elasticsearch time range query. You can use DateTimeFormatterBuilder, or
; always use (formatters :data-time) formatter.
;

; globals
(def ^:dynamic *es-conn*)


(def elasticserver "localhost")
(def elasticport 9200)

(def drug-index-name "drug")  ; exports namespace global var
(def drug-index-info-type-name "info") ; prescribing highlights of info 
(def contraindication-index-name "contraindication")
(def interaction-index-name "interaction")

; prescribing highlight sections. drug index highlight info type mapping type fields
(def sections ["drug" "usage" "dosage" "contraindication" "precaution" "reaction" "interaction"])

; forward declaration
(declare query-contraindication)
(declare query-interaction)

; wrap connecting fn
(defn connect [host port]
  (esr/connect! (str "http://" host ":" port)))


; an index may store documents of different “mapping types”. 
; mapping types can be thought of as column schemas of a table in a db(index)
; each field has a mapping type. A mapping type defines how a field is analyzed, indexed so can be searched.
; each index has one mapping type. index.my_type.my_field. Each (mapping)type can have many mapping definitions.
; curl -XGET localhost:9200/dodgersdata/data/_mapping?pretty=true
; http://www.elasticsearch.org/guide/reference/mapping/core-types/
(defn create-drug-info-mapping-type
  "ret a mapping type for drug index with all types of string"
  [mapping-name]
  (let [section-type {:type "string"}  ; each section assoced with section-type
        schema (reduce #(assoc %1 (keyword %2) section-type) {} sections)]
    (hash-map mapping-name {:properties schema})))


; index is db and each mapping types in index is a table.
(defn create-index
  "create index with the passing name and mapping types only "
  [idxname mappings]
  (if-not (esi/exists? idxname)  ; create index only when does not exist
    (esi/create idxname :mappings mappings)))


(defn create-drug-doc
  "create a document for drug index from a list of sections json"
  [drugname info]
  (apply merge (hash-map (keyword (nth sections 0)) drugname) info))


(defn create-drug-index
  "create drug index to store highlights of prescribing info"
  []
  (let [info-mapping-type (create-drug-info-mapping-type drug-index-info-type-name)]
    (prn "info-mapping-type " info-mapping-type)
    (create-index drug-index-name info-mapping-type)))


(defn insert-drug-doc
  "populate drug index with data parsing from pdf"
  [drugname & sections]  ; encapsulate into a list of sections
  (let [mapping (esi/get-mapping drug-index-name drug-index-info-type-name)
        drug-doc (create-drug-doc drugname sections)]
    (prn "inserting drug doc " drug-doc)
    (esd/create drug-index-name drug-index-info-type-name drug-doc)))


(defn drug-info-query-string [section keyname]
  "query term in drug index info mapping in field"
  (let [now (clj-time/now) 
        pre (clj-time/minus now (clj-time/hours 20))  ; from now back 1 days
        nowfmt (clj-time.format/unparse (clj-time.format/formatters :date-time) now)]
    (q/query-string
      :fields sections
      :query (str keyname))))


(defn elastic-query [idxname query process-fn]
  ; if idxname is unknown, we can use search-all-indexes-and-types.
  ; query range to be 
  ;(connect "localhost" 9200)           
  (let [res (esd/search-all-types idxname   ; drug
              :size 10        ; limits to 10 results
              :query query
              :sort {"drug" {"order" "desc"}})  ; 
         n (esrsp/total-hits res)
         hits (esrsp/hits-from res)  ; searched out docs in hits array
         facets (esrsp/facets-from res)]  ; facets
    (println (format "Total hits: %d" n))
    (process-fn hits)))


(defn process-hits
  "searched out docs are in hits ary, iterate the list"
  [hits]
  (prn " === " hits))


(defn search
  "search drug index of certain field(section) of the keyword"
  [drugname section qword]
  (let [search-fields (if (nil? section) sections section)
        qstring (drug-info-query-string search-fields qword)]
    (elastic-query drug-index-name qstring process-hits)))