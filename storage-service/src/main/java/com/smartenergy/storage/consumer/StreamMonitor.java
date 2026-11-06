package com.smartenergy.storage.consumer;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Expose l'état du stream : longueur et messages en attente d'accusé. */
@Component
public class StreamMonitor {

    private final StringRedisTemplate redis;
    private final String stream;
    private final String group;

    public StreamMonitor(StringRedisTemplate redis,
                         MeterRegistry registry,
                         @Value("${smartenergy.stream}") String stream,
                         @Value("${smartenergy.group}") String group) {
        this.redis = redis;
        this.stream = stream;
        this.group = group;
        Gauge.builder("smartenergy.redis.pending", this, StreamMonitor::pendingMessages).register(registry);
    }

    public long streamLength() {
        Long size = redis.opsForStream().size(stream);
        return size == null ? 0 : size;
    }

    public long pendingMessages() {
        try {
            PendingMessagesSummary summary = redis.opsForStream().pending(stream, group);
            return summary == null ? 0 : summary.getTotalPendingMessages();
        } catch (RuntimeException e) {
            return 0; // le groupe n'existe pas encore
        }
    }
}
