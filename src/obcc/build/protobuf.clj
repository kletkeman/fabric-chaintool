(ns obcc.build.protobuf
  (:refer-clojure :exclude [compile])
  (:import [org.stringtemplate.v4 STGroupFile ST])
  (:import [java.util ArrayList])
  (:require [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [instaparse.core :as insta]
            [obcc.util :as util]
            [obcc.ast :as ast]
            [obcc.config.parser :as config]
            [obcc.build.interface :as intf]))

;; types to map to java objects that string template expects.
;;

(deftype Field    [^String modifier ^String type ^String name ^String index])
(deftype Message  [^String name ^ArrayList fields])

;; scalar types should just be passed naked.  user types should be fully qualified
(defn typeconvert [namespace [type name]]
  (if (= type :scalar)
      name
      (util/qualifyname namespace name)))

;;-----------------------------------------------------------------
;; buildX - build our ST friendly objects from the AST
;;-----------------------------------------------------------------
(defn buildfields [namespace fields]
  (into {} (map (fn [[index {:keys [modifier type fieldName]}]]
                  (vector index (->Field modifier (typeconvert namespace type) fieldName index))) fields)))

(defn buildmessage [namespace [name fields]]
  (->Message (util/qualifyname namespace name) (buildfields namespace fields)))

(defn buildmessages [namespace ast]
  (map #(buildmessage namespace %) (intf/getmessages ast)))

(defn buildallmessages [ast namespaces]
  (let [msgs (->> ast (map (fn [[namespace ast]] (buildmessages (namespaces namespace) ast))) flatten)]
    (into {} (map #(vector (.name %) %) msgs))))

;;-----------------------------------------------------------------
;; generate protobuf output - compiles the interfaces into a
;; protobuf specification, suitable for writing to a file or
;; passing to protoc
;;-----------------------------------------------------------------
(defn generateproto [interfaces namespaces]
  (let [messages (buildallmessages interfaces namespaces)
        stg  (STGroupFile. "generators/proto.stg")
        template (.getInstanceOf stg "protobuf")]

    (.add template "messages" messages)
    (.render template)))

;;-----------------------------------------------------------------
;; compile - generates a protobuf specification and writes it to
;; the default location in the build area
;;-----------------------------------------------------------------
(defn compile [path interfaces namespaces]
  (let [protobuf (generateproto interfaces namespaces)
        protofile (io/file path util/supportpath "wireprotocol.proto")]

    ;; ensure the path exists
    (io/make-parents protofile)

    ;; and emit our output
    (with-open [output (io/writer protofile :truncate true)]
      (.write output protobuf))

    ;; finally, return the name of the file so other tools (like protoc) may consume it
    protofile))
