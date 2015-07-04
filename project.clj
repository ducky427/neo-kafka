(def neo4j-version "2.2.3")

(defproject neo-kafka "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :aot :all
  :omit-source true
  :uberjar-name "neo-kafka.jar"
  :profiles {:provided {:dependencies [[org.neo4j/neo4j ~neo4j-version]
                                       [org.neo4j/neo4j-kernel ~neo4j-version :classifier "tests" :scope "test"]

                                       ;; for testing
                                       [org.neo4j.test/neo4j-harness ~neo4j-version :scope "test"]
                                       [org.neo4j/neo4j-io ~neo4j-version :classifier "tests" :scope "test"]
                                       [clj-http "1.1.2"]]}}
  :prep-tasks [["compile" "neo-kafka.core"]
                "javac" "compile"]
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.apache.kafka/kafka-clients "0.8.2.1"]
                 [com.damballa/abracad "0.4.12"]]
  :plugins      [[lein-kibit "0.1.2"]
                 [lein-ancient "0.6.7"]
                 [jonase/eastwood "0.2.1"]
                 [lein-cljfmt "0.2.0"]])
