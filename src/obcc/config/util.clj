;; Licensed to the Apache Software Foundation (ASF) under one
;; or more contributor license agreements.  See the NOTICE file
;; distributed with this work for additional information
;; regarding copyright ownership.  The ASF licenses this file
;; to you under the Apache License, Version 2.0 (the
;; "License"); you may not use this file except in compliance
;; with the License.  You may obtain a copy of the License at
;;
;;   http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing,
;; software distributed under the License is distributed on an
;; "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
;; KIND, either express or implied.  See the License for the
;; specific language governing permissions and limitations
;; under the License.

(ns obcc.config.util
  (:require [clojure.java.io :as io]
            [clojure.zip :as zip]
            [obcc.config.parser :as config.parser])
  (:refer-clojure :exclude [load find]))

(def configname "chaincode.conf")

(defn load [path]
  (let [file (io/file path configname)]
    (cond

      (not (.isFile file))
      (throw (Exception. (str (.getAbsolutePath file) " not found")))

      :else
      (config.parser/from-file file))))

(defn load-from-options [options]
  (let [path (:path options)
        config (load path)]
    [path config]))

(defn isnode? [node] (when (and (vector? node) (keyword? (first node))) :true))
(defn nodename [node] (when (isnode? node) (first node)))

(defn fqp [_loc]
  (loop [loc _loc
         path ()]

    (cond
      (nil? loc)
      (vec path)

      :else
      (recur (zip/up loc) (->> loc zip/node nodename (conj path))))))

(defn buildpath [pathspec] (->> pathspec (concat [:configuration]) vec))

(defn find [config pathspec]
  (let [fqpathspec (buildpath pathspec)]
    (loop [loc config]
      (cond
        (zip/end? loc)
        nil

        (= (fqp loc) fqpathspec)
        (->> loc zip/node rest vec)

        :else
        (recur (zip/next loc))))))

(defn findfirst [config pathspec] (first (find config pathspec)))

(defn compositename [config]
  (let [name (findfirst config [:name])
        version (findfirst config [:version])]
    (str name "-" version)))
