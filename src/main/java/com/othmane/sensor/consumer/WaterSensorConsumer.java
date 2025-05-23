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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@WebListener
public class WaterSensorConsumer implements Runnable, ServletContextListener {
    // Configuration
    private static final String INFLUX_URL = System.getenv().getOrDefault("INFLUX_URL", "http://localhost:8086");
    private static final String INFLUX_TOKEN = System.getenv().getOrDefault("INFLUX_TOKEN", "my-super-secret-auth-token");
    private static final String INFLUX_ORG = System.getenv().getOrDefault("INFLUX_ORG", "smartcity");
    private static final String INFLUX_BUCKET = System.getenv().getOrDefault("INFLUX_BUCKET", "sensor_data");
    private static final String KAFKA_TOPIC = "consommation-eau";
    private static final String KAFKA_BROKER = System.getenv().getOrDefault("KAFKA_BROKER", "localhost:9092");
    private static final String ALERT_WEBHOOK_URL = System.getenv().getOrDefault("ALERT_WEBHOOK_URL", "http://alert-service:8080/api/alerts");

    // Seuil d'alerte (en litres/minute)
    private static final double CONSOMMATION_ALERTE_SEUIL = 4;

    private ExecutorService executor;
    private InfluxDBClient influxDBClient;
    private HttpClient httpClient;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("==== INITIALISATION DU CONSOMMATEUR CAPTEUR EAU ====");
        System.out.println("Configuration des alertes - Seuil: " + CONSOMMATION_ALERTE_SEUIL + " L/min");
        System.out.println("Webhook d'alerte: " + ALERT_WEBHOOK_URL);

        try {
            influxDBClient = InfluxDBClientFactory.create(INFLUX_URL, INFLUX_TOKEN.toCharArray(), INFLUX_ORG, INFLUX_BUCKET);
            httpClient = HttpClient.newHttpClient();
            executor = Executors.newSingleThreadExecutor();
            executor.submit(this);
        } catch (Exception e) {
            System.err.println("Échec de l'initialisation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("==== ARRÊT DU CONSOMMATEUR CAPTEUR EAU ====");
        if (executor != null) executor.shutdownNow();
        if (influxDBClient != null) influxDBClient.close();
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

        try (Consumer<String, String> consumer = new KafkaConsumer<>(props);
             WriteApi writeApi = influxDBClient.getWriteApi()) {

            consumer.subscribe(Collections.singletonList(KAFKA_TOPIC));

            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    processRecordWithAlerts(record, writeApi);
                }
                consumer.commitAsync();
            }
        } catch (Exception e) {
            System.err.println("ERREUR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processRecordWithAlerts(ConsumerRecord<String, String> record, WriteApi writeApi) {
        try {
            JSONObject message = new JSONObject(record.value());
            String sensorId = message.getString("sensor_id");
            double consommation = message.getDouble("consommation_L_min");
            double quality = message.getDouble("quality");
            Instant timestamp = Instant.parse(message.getString("timestamp"));

            // Point de données pour InfluxDB
            Point point = Point.measurement("water_consumption")
                    .addTag("sensor_id", sensorId)
                    .addField("consumption_L_min", consommation)
                    .addField("quality", quality)
                    .time(timestamp, WritePrecision.MS);

            writeApi.writePoint(point);

            // Vérification des alertes
            if (consommation > CONSOMMATION_ALERTE_SEUIL) {
                triggerHighConsumptionAlert(sensorId, consommation, timestamp);
            }

            System.out.printf("[%s] Capteur: %s | Consommation: %.2f L/min | Qualité: %.1f%n",
                    timestamp, sensorId, consommation, quality);

        } catch (Exception e) {
            System.err.println("Erreur de traitement: " + record.value());
            System.err.println("Détails: " + e.getMessage());
        }
    }

    private void triggerHighConsumptionAlert(String sensorId, double consommation, Instant timestamp) {
        // 1. Store alert in InfluxDB
        Point alertPoint = Point.measurement("water_alerts")
                .addTag("sensor_id", sensorId)
                .addTag("alert_type", "high_consumption")
                .addField("value", consommation)
                .addField("threshold", CONSOMMATION_ALERTE_SEUIL)
                .time(timestamp, WritePrecision.MS);

        try (WriteApi writeApi = influxDBClient.getWriteApi()) {
            writeApi.writePoint(alertPoint);

            // 2. Send to external alert endpoint
            sendWebhookAlert(sensorId, consommation, timestamp);

            System.err.printf("!!! ALERTE !!! [%s] Capteur %s: Consommation élevée %.2f L/min (seuil: %.2f)%n",
                    timestamp, sensorId, consommation, CONSOMMATION_ALERTE_SEUIL);

        } catch (Exception e) {
            System.err.println("Échec de l'enregistrement de l'alerte: " + e.getMessage());
        }
    }

    private void sendWebhookAlert(String sensorId, double consommation, Instant timestamp) {
        try {
            JSONObject alertPayload = new JSONObject();
            alertPayload.put("sensor_id", sensorId);
            alertPayload.put("consumption", consommation);
            alertPayload.put("threshold", CONSOMMATION_ALERTE_SEUIL);
            alertPayload.put("timestamp", timestamp.toString());
            alertPayload.put("alert_type", "high_water_consumption");
            alertPayload.put("severity", "warning");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ALERT_WEBHOOK_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(alertPayload.toString()))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(response -> System.out.println("Alert sent successfully. Response: " + response))
                    .exceptionally(e -> {
                        System.err.println("Failed to send alert: " + e.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            System.err.println("Error creating webhook request: " + e.getMessage());
        }
    }
}