package com.prophis;

import java.util.HashMap;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.graphdb.factory.Description;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.extension.KernelExtensionFactory;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;

import neo_kafka.core.KafkaTransactionEventHandler;

import static org.neo4j.helpers.Settings.setting;
import static org.neo4j.helpers.Settings.STRING;


public class KafkaDiffKernelExtensionFactory extends KernelExtensionFactory<KafkaDiffKernelExtensionFactory.Dependencies> {
    public static final String SERVICE_NAME = "KAFKA_DIFF";

    public interface Dependencies {
        GraphDatabaseService getGraphDatabaseService();

        Config getConfig();
    }

    public KafkaDiffKernelExtensionFactory() {
        super(SERVICE_NAME);
    }

    @Description("Settings for the Kafka Diff Extension")
    public static abstract class KafkaDiffSettings {
        public static Setting<String> servers = setting("kafka.servers", STRING, "localhost:9092");
        public static Setting<String> node_topic = setting("kafka.node_topic", STRING, "nodes");
        public static Setting<String> relationship_topic = setting("kafka.relationship_topic", STRING, "relationships");
    }

    @Override
    public Lifecycle newKernelExtension(final Dependencies dependencies) throws Throwable {
        return new LifecycleAdapter() {

            private KafkaTransactionEventHandler handler;
            private KafkaProducer producer;

            @Override
            public void start() throws Throwable {
                Config config = dependencies.getConfig();

                // https://stackoverflow.com/questions/28755309/default-serializer-for-kafka-0-8-2-0
                HashMap<String, String> kafka_config = new HashMap<String, String>() {{
                    put("bootstrap.servers", config.get(KafkaDiffSettings.servers));
                    put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                    put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
                    put("compression.type", "snappy");
                }};
                String node_topic = config.get(KafkaDiffSettings.node_topic);
                String rels_topic = config.get(KafkaDiffSettings.relationship_topic);
                producer = new KafkaProducer(kafka_config);
                handler = new KafkaTransactionEventHandler(dependencies.getGraphDatabaseService(), producer, node_topic, rels_topic);
                dependencies.getGraphDatabaseService().registerTransactionEventHandler(handler);
            }

            @Override
            public void shutdown() throws Throwable {
                if (producer != null) producer.close();
                dependencies.getGraphDatabaseService().unregisterTransactionEventHandler(handler);
            }
        };
    }

}
