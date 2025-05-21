package com.othmane.sensor.consumer;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.apache.kafka.clients.consumer.*;
import org.json.JSONObject;

import java.time.Instant;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@WebListener
public class WaterSensorConsumer implements Runnable, ServletContextListener {
    // Environment-aware configuration
    private static final String INFLUX_URL = System.getenv().getOrDefault("INFLUX_URL", "http://localhost:8086");
    private static final String INFLUX_TOKEN = System.getenv().getOrDefault("INFLUX_TOKEN", "my-super-secret-auth-token");
    private static final String INFLUX_ORG = System.getenv().getOrDefault("INFLUX_ORG", "smartcity");
    private static final String INFLUX_BUCKET = System.getenv().getOrDefault("INFLUX_BUCKET", "sensor_data");
    private static final String KAFKA_TOPIC = "consommation-eau";
    private static final String KAFKA_BROKER = System.getenv().getOrDefault("KAFKA_BROKER", "localhost:9092");

    private ExecutorService executor;
    private InfluxDBClient influxDBClient;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("==== STARTING WATER SENSOR CONSUMER ====");
        System.out.println("Connecting to Kafka at: " + KAFKA_BROKER);
        System.out.println("Connecting to InfluxDB at: " + INFLUX_URL);

        try {
            influxDBClient = InfluxDBClientFactory.create(INFLUX_URL, INFLUX_TOKEN.toCharArray(), INFLUX_ORG, INFLUX_BUCKET);
            executor = Executors.newSingleThreadExecutor();
            executor.submit(this);
        } catch (Exception e) {
            System.err.println("Initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("==== STOPPING WATER SENSOR CONSUMER ====");
        if (executor != null) {
            executor.shutdownNow();
        }
        if (influxDBClient != null) {
            influxDBClient.close();
        }
    }

    @Override
    public void run() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKER);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "water-consumer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "water-consumer-" + System.currentTimeMillis());

        try (Consumer<String, String> consumer = new KafkaConsumer<>(props);
             WriteApi writeApi = influxDBClient.getWriteApi()) {

            consumer.subscribe(Collections.singletonList(KAFKA_TOPIC));
            System.out.println("Kafka consumer ready. Waiting for messages...");

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    if (!records.isEmpty()) {
                        System.out.println("Received " + records.count() + " messages");

                        for (ConsumerRecord<String, String> record : records) {
                            processRecord(record, writeApi);
                        }
                        consumer.commitAsync();
                    }
                } catch (Exception e) {
                    System.err.println("Error processing batch: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processRecord(ConsumerRecord<String, String> record, WriteApi writeApi) {
        try {
            JSONObject message = new JSONObject(record.value());

            Point point = Point.measurement("water_consumption")
                    .addTag("sensor_id", message.getString("sensor_id"))
                    .addTag("location", message.getString("location"))
                    .addField("flow_rate", message.getDouble("flow_rate"))
                    .addField("pressure", message.getDouble("pressure"))
                    .addField("temperature", message.getDouble("temperature"))
                    .time(Instant.parse(message.getString("timestamp")), WritePrecision.MS);

            writeApi.writePoint(point);
            System.out.println("Processed: " + message.getString("sensor_id") +
                    " at " + message.getString("timestamp"));

        } catch (Exception e) {
            System.err.println("Failed to process record: " + record.value());
            System.err.println("Error: " + e.getMessage());
        }
    }
}