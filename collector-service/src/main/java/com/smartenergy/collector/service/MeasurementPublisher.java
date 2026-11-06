package com.smartenergy.collector.service;

import com.smartenergy.collector.api.EnergyMeasurementRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MeasurementPublisher {

    private static final Logger log = LoggerFactory.getLogger(MeasurementPublisher.class);

    private final StringRedisTemplate redis;
    private final String stream;
    private final Counter received;

    public MeasurementPublisher(StringRedisTemplate redis,
                                @Value("${smartenergy.stream}") String stream,
                                MeterRegistry registry) {
        this.redis = redis;
        this.stream = stream;
        this.received = registry.counter("smartenergy.measurements.received");
    }

    public RecordId publish(EnergyMeasurementRequest request) {
        RecordId id = redis.opsForStream().add(stream, toFields(request));
        received.increment();
        log.info("Mesure publiée sensorId={} messageId={}", request.sensorId(), id);
        return id;
    }

    static Map<String, String> toFields(EnergyMeasurementRequest request) {
        Map<String, String> fields = new HashMap<>();
        fields.put("sensorId", request.sensorId());
        fields.put("timestamp", request.timestamp().toString());
        fields.put("powerKw", request.powerKw().toString());
        fields.put("voltage", request.voltage().toString());
        if (request.temperature() != null) {
            fields.put("temperature", request.temperature().toString());
        }
        return fields;
    }
}
