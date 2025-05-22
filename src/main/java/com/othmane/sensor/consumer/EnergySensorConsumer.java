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
public class EnergySensorConsumer implements Runnable, ServletContextListener {
    private static final String INFLUX_URL = System.getenv().getOrDefault("INFLUX_URL", "http://localhost:8086");
    private static final String INFLUX_TOKEN = System.getenv().getOrDefault("INFLUX_TOKEN", "my-super-secret-auth-token");
    private static final String INFLUX_ORG = System.getenv().getOrDefault("INFLUX_ORG", "smartcity");
    private static final String INFLUX_BUCKET = System.getenv().getOrDefault("INFLUX_BUCKET", "sensor_data");
    private static final String KAFKA_TOPIC = "energy-usage";
    private static final String KAFKA_BROKER = System.getenv().getOrDefault("KAFKA_BROKER", "localhost:9092");

    // Seuils d'alerte
    private static final double POWER_ALERT_THRESHOLD = 8.0; // kW
    private static final double VOLTAGE_ALERT_THRESHOLD_HIGH = 240.0; // V
    private static final double VOLTAGE_ALERT_THRESHOLD_LOW = 210.0; // V

    private ExecutorService executor;
    private InfluxDBClient influxDBClient;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("==== INITIALISATION DU CONSOMMATEUR CAPTEUR ÉNERGIE ====");
        System.out.println("Seuils d'alerte - Puissance: " + POWER_ALERT_THRESHOLD + " kW");
        System.out.println("Seuils d'alerte - Tension: " + VOLTAGE_ALERT_THRESHOLD_LOW + "-" + VOLTAGE_ALERT_THRESHOLD_HIGH + " V");

        try {
            influxDBClient = InfluxDBClientFactory.create(INFLUX_URL, INFLUX_TOKEN.toCharArray(), INFLUX_ORG, INFLUX_BUCKET);
            executor = Executors.newSingleThreadExecutor();
            executor.submit(this);
        } catch (Exception e) {
            System.err.println("Échec de l'initialisation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("==== ARRÊT DU CONSOMMATEUR CAPTEUR ÉNERGIE ====");
        if (executor != null) executor.shutdownNow();
        if (influxDBClient != null) influxDBClient.close();
    }

    @Override
    public void run() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKER);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "energy-consumer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        try (Consumer<String, String> consumer = new KafkaConsumer<>(props);
             WriteApi writeApi = influxDBClient.getWriteApi()) {

            consumer.subscribe(Collections.singletonList(KAFKA_TOPIC));

            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    processEnergyRecord(record, writeApi);
                }
                consumer.commitAsync();
            }
        } catch (Exception e) {
            System.err.println("ERREUR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processEnergyRecord(ConsumerRecord<String, String> record, WriteApi writeApi) {
        try {
            JSONObject message = new JSONObject(record.value());
            String sensorId = message.getString("sensor_id");
            double power = message.getDouble("power_kW");
            double voltage = message.getDouble("voltage");
            Instant timestamp = Instant.parse(message.getString("timestamp"));

            // Point de données pour InfluxDB
            Point point = Point.measurement("energy_usage")
                    .addTag("sensor_id", sensorId)
                    .addField("power_kW", power)
                    .addField("voltage", voltage)
                    .time(timestamp, WritePrecision.MS);

            writeApi.writePoint(point);

            // Vérification des alertes
            if (power > POWER_ALERT_THRESHOLD) {
                triggerAlert("energy_alerts", sensorId, "high_power", power, POWER_ALERT_THRESHOLD, timestamp);
            }
            if (voltage > VOLTAGE_ALERT_THRESHOLD_HIGH) {
                triggerAlert("energy_alerts", sensorId, "high_voltage", voltage, VOLTAGE_ALERT_THRESHOLD_HIGH, timestamp);
            }
            if (voltage < VOLTAGE_ALERT_THRESHOLD_LOW) {
                triggerAlert("energy_alerts", sensorId, "low_voltage", voltage, VOLTAGE_ALERT_THRESHOLD_LOW, timestamp);
            }

            System.out.printf("[%s] Capteur: %s | Puissance: %.2f kW | Tension: %.1f V%n",
                    timestamp, sensorId, power, voltage);

        } catch (Exception e) {
            System.err.println("Erreur de traitement: " + record.value());
            System.err.println("Détails: " + e.getMessage());
        }
    }

    private void triggerAlert(String measurement, String sensorId, String alertType,
                              double value, double threshold, Instant timestamp) {
        Point alertPoint = Point.measurement(measurement)
                .addTag("sensor_id", sensorId)
                .addTag("alert_type", alertType)
                .addField("value", value)
                .addField("threshold", threshold)
                .time(timestamp, WritePrecision.MS);

        try (WriteApi writeApi = influxDBClient.getWriteApi()) {
            writeApi.writePoint(alertPoint);
            System.err.printf("!!! ALERTE !!! [%s] Capteur %s: %s %.2f (seuil: %.2f)%n",
                    timestamp, sensorId, alertType.replace("_", " "), value, threshold);
        } catch (Exception e) {
            System.err.println("Échec de l'enregistrement de l'alerte: " + e.getMessage());
        }
    }
}