# neo-kafka

This Neo4j Kernel Extension connects pushes all the data changes in Neo4j to a [Kafka](https://kafka.apache.org/) broker.

To build it, install [Leiningen](http://leiningen.org/#install) and run `lein uberjar`. Copy the generated `target/neo-kafka.jar` file to `$NEO4J/plugins` folder. You will also need to add the following to `$NEO4J/conf/neo4j.properties`:

```
kafka.servers=localhost:9092
kafka.node_topic=nodes
kafka.relationship_topic=edges
```

This specifies the location of the Kafka broker and the topics to which the node and the edges changes are sent to.

The key of a message to the node topic is a long which represents the Neo4j node ID. The key of a message to the edge topic is a long with the Neo4j relationship ID. The contents of both messages is encoded using [Apache Avro](https://avro.apache.org/).

A sample message value for a message sent to the node topic looks like:

```
{:timestamp 1436022479993, :created true, :deleted nil, :new-lbls ["Country"], :del-lbls nil, :new-props {"name" "Belgium"}, :del-props nil}
```

A sample message value for a message sent to the edge topic looks like:

```
{:timestamp 1436035131528, :created true, :deleted nil, :from 205, :to 253, :name "LIVES_IN", :new-props nil, :del-props nil}
```

## Other commands

Start Zookeeper:

    bin/zookeeper-server-start.sh config/zookeeper.properties

Now start the Kafka server:

    bin/kafka-server-start.sh config/server.properties

Create a topic called `test`:

    bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic test

## License

Copyright © 2015 Rohit Aggarwal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
