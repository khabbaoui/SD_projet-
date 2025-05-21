package com.othmane.sensor.resource;
import com.othmane.sensor.consumer.WaterSensorConsumer;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/sensor")
public class SensorResource {
    @GET
    @Path("/start-consumer")
    public String startConsumer() {
        new Thread(new WaterSensorConsumer()).start();
        return "Kafka consumer started! hiiii";
    }
}