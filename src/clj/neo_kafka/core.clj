(ns neo-kafka.core
  (:require [abracad.avro :as avro])
  (:import (org.apache.kafka.clients.producer KafkaProducer ProducerRecord)
           (org.neo4j.graphdb GraphDatabaseService Node Relationship)
           (org.neo4j.graphdb.event LabelEntry PropertyEntry TransactionData TransactionEventHandler)))

(def node-schema
  (avro/parse-schema
   {:type :record
    :name "NeoNodeDiff"
    :fields [{:name "timestamp" :type :long}
             {:name "created" :type [:boolean :null]}
             {:name "deleted" :type [:boolean :null]}
             {:name "new-lbls" :type [:null {:type :array :items :string}]}
             {:name "del-lbls" :type [:null {:type :array :items :string}]}
             {:name "new-props" :type [:null {:type :map :values [:boolean :int :double :string :null]}]}
             {:name "del-props" :type [:null {:type :array :items :string}]}]}))

(def edge-schema
  (avro/parse-schema
   {:type :record
    :name "NeoEdgeDiff"
    :fields [{:name "timestamp" :type :long}
             {:name "created" :type [:boolean :null]}
             {:name "deleted" :type [:boolean :null]}
             {:name "from" :type [:long :null]}
             {:name "to" :type [:long :null]}
             {:name "name" :type [:string :null]}
             {:name "new-props" :type [:null {:type :map :values [:boolean :int :double :string :null]}]}
             {:name "del-props" :type [:null {:type :array :items :string}]}]}))

(defn- get-le-id
  [^LabelEntry x]
  (-> x .node .getId))

(defn- get-npe-id
  [^PropertyEntry x]
  (.getId ^Node (.entity x)))

(defn- get-ppe-id
  [^PropertyEntry x]
  (.getId ^Relationship (.entity x)))

(defn make-props
  [^Iterable xs ys id-fn i]
  (reduce
   (fn [prev ^PropertyEntry x]
     (let [nid  (id-fn x)]
       (assoc-in prev [nid i (.key x)] (.value x))))
   ys
   xs))

(defn get-prop-names
  [^Iterable xs ys id-fn i]
  (let [groups  (group-by id-fn xs)]
    (reduce-kv (fn [prev k v]
                 (assoc-in prev [k i]
                           (distinct
                            (map
                             (fn [^PropertyEntry x] (.key x))
                             v))))
               ys
               groups)))

(defn make-labels
  [^Iterable xs ys i]
  (let [groups   (group-by get-le-id xs)]
    (reduce-kv (fn [prev k v]
                 (assoc-in prev [k i]
                           (distinct
                            (map
                             (fn [^LabelEntry x] (-> x .label .name))
                             v))))
               ys
               groups)))

(defn make-nodes
  [^Iterable xs ys k]
  (reduce
   (fn [prev ^Node x]
     (assoc-in prev [(.getId x) k] true))
   ys
   xs))

(defn make-rels
  [^Iterable xs ys id-fn k]
  (reduce
   (fn [prev ^Relationship x]
     (assoc-in prev [(id-fn x) k] true))
   ys
   xs))

(defn handle-nodes
  [^KafkaProducer producer ^String topic ^TransactionData td current-time]
  (let [res   (make-nodes (.createdNodes td) {} :created)
        res   (make-labels (.assignedLabels td) res :new-lbls)
        res   (make-labels (.removedLabels td) res :del-lbls)
        res   (make-props (.assignedNodeProperties td) res get-npe-id :new-props)
        res   (get-prop-names (.removedNodeProperties td) res get-npe-id :del-props)
        res   (merge res (make-nodes (.deletedNodes td) {} :deleted))]
    (doseq [[k v]  res
            :let   [p (ProducerRecord. topic (str k) (avro/binary-encoded node-schema
                                                                          (assoc v :timestamp current-time)))]]
      (.send producer p))))

(defn rel-id-fn
  [^Relationship x]
  [(.getId x)
   (.getId (.getStartNode x))
   (.getId (.getEndNode x))
   (.name (.getType x))])

(defn ppe-id-fn
  [^PropertyEntry x]
  (rel-id-fn ^Relationship (.entity x)))

(defn handle-edges
  [^KafkaProducer producer ^String topic ^TransactionData td current-time]
  (let [res        (make-rels (.createdRelationships td) {} rel-id-fn :created)
        res        (make-props (.assignedRelationshipProperties td) res ppe-id-fn :new-props)
        res        (get-prop-names (.removedRelationshipProperties td) res ppe-id-fn :del-props)
        res        (merge res (make-rels (.deletedRelationships td) {} rel-id-fn :deleted))]
    (doseq [[[k from-id to-id rel-name] v]  res
            :let [p  (ProducerRecord. topic (str k) (avro/binary-encoded edge-schema
                                                                            (assoc v
                                                                                   :timestamp current-time
                                                                                   :from from-id
                                                                                   :to to-id
                                                                                   :name rel-name)))]]
      (.send producer p))))

(defn run-logic
  [^KafkaProducer producer ^String node-topic ^String rels-topic ^TransactionData td]
  (let [current-time  (System/currentTimeMillis)]
    (handle-nodes producer node-topic td current-time)
    (handle-edges producer rels-topic td current-time)))

;; See https://groups.google.com/d/topic/neo4j/9Y6LlWEM0xc/discussion
;; https://groups.google.com/d/topic/neo4j-ecosystem/3ky_DyouVmM/discussion
;; https://github.com/maxdemarzi/neo_chronicle_index
;; https://github.com/neo4j-contrib/neo4j-elasticsearch
;; http://maxdemarzi.com/2015/03/25/triggers-in-neo4j/
;; https://github.com/maxdemarzi/neo_listens


(deftype KafkaTransactionEventHandler [^GraphDatabaseService db ^KafkaProducer producer ^String node-topic ^String rels-topic]
  TransactionEventHandler
  (beforeCommit
    [this ^TransactionData data]
    nil)
  (afterCommit
    [this data state]
    (require 'neo-kafka.core)
    (run-logic producer node-topic rels-topic data)
    nil)
  (afterRollback
    [this data state]
    nil))
