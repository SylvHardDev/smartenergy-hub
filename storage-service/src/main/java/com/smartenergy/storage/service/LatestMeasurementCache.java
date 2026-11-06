package com.smartenergy.storage.service;

import com.smartenergy.storage.model.MeasurementEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Dernière mesure par capteur dans Redis. Le cache n'est pas la source de vérité : un échec n'interrompt rien. */
@Service
public class LatestMeasurementCache {

    private static final Logger log = LoggerFactory.getLogger(LatestMeasurementCache.class);
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public LatestMeasurementCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void update(MeasurementEntity entity) {
        String key = "latest:" + entity.getKey().getSensorId();
        Instant measuredAt = entity.getKey().getMeasuredAt();
        try {
            Object current = redis.opsForHash().get(key, "timestamp");
            if (current != null && Instant.parse(current.toString()).isAfter(measuredAt)) {
                return; // un rejeu ou un message en retard ne doit pas remplacer une mesure plus récente
            }
            Map<String, String> fields = new HashMap<>();
            fields.put("timestamp", measuredAt.toString());
            fields.put("powerKw", entity.getPowerKw().toString());
            fields.put("voltage", entity.getVoltage().toString());
            if (entity.getTemperature() != null) {
                fields.put("temperature", entity.getTemperature().toString());
            }
            redis.opsForHash().putAll(key, fields);
            redis.expire(key, TTL);
        } catch (RuntimeException e) {
            log.warn("Mise à jour du cache impossible pour {} : {}", key, e.getMessage());
        }
    }
}
