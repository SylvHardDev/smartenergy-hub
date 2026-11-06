package com.smartenergy.storage.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Consommateur du groupe storage-group.
 * Règle centrale : XACK uniquement après persistance réussie. Un message non accusé reste dans la
 * liste des messages en attente (PEL) et il est relu au démarrage, puis périodiquement.
 */
@Component
public class StreamConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StreamConsumer.class);

    private final StringRedisTemplate redis;
    private final MeasurementProcessor processor;
    private final String stream;
    private final String deadLetterStream;
    private final String group;
    private final Consumer consumer;
    private final int batchSize;
    private final Duration pendingRetry;
    private final Counter persisted;
    private final Counter persistErrors;
    private final Counter deadLettered;
    private final Timer processingTime;

    private volatile boolean running;
    private Thread thread;

    public StreamConsumer(StringRedisTemplate redis,
                          MeasurementProcessor processor,
                          MeterRegistry registry,
                          @Value("${smartenergy.stream}") String stream,
                          @Value("${smartenergy.dead-letter-stream}") String deadLetterStream,
                          @Value("${smartenergy.group}") String group,
                          @Value("${smartenergy.consumer}") String consumerName,
                          @Value("${smartenergy.batch-size}") int batchSize,
                          @Value("${smartenergy.pending-retry-seconds}") long pendingRetrySeconds) {
        this.redis = redis;
        this.processor = processor;
        this.stream = stream;
        this.deadLetterStream = deadLetterStream;
        this.group = group;
        this.consumer = Consumer.from(group, consumerName);
        this.batchSize = batchSize;
        this.pendingRetry = Duration.ofSeconds(pendingRetrySeconds);
        this.persisted = registry.counter("smartenergy.measurements.persisted");
        this.persistErrors = registry.counter("smartenergy.persistence.errors");
        this.deadLettered = registry.counter("smartenergy.measurements.deadlettered");
        this.processingTime = registry.timer("smartenergy.processing.time");
    }

    private void run() {
        long nextPendingCheck = 0;
        while (running) {
            try {
                ensureGroup();
                if (System.currentTimeMillis() >= nextPendingCheck) {
                    processPending();
                    nextPendingCheck = System.currentTimeMillis() + pendingRetry.toMillis();
                }
                readNew();
            } catch (RuntimeException e) {
                if (running) {
                    log.error("Lecture du stream impossible, nouvel essai dans 2 s : {}", e.getMessage());
                    pause(2000);
                }
            }
        }
    }

    private void processPending() {
        // L'offset 0 relit les messages livrés à ce consommateur mais jamais accusés.
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                consumer,
                StreamReadOptions.empty().count(batchSize),
                StreamOffset.create(stream, ReadOffset.from("0")));
        if (records != null && !records.isEmpty()) {
            log.info("Reprise de {} message(s) en attente", records.size());
            handle(records);
        }
    }

    private void readNew() {
        // ">" : uniquement les messages jamais livrés au groupe.
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                consumer,
                StreamReadOptions.empty().count(batchSize).block(Duration.ofSeconds(2)),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));
        if (records != null) {
            handle(records);
        }
    }

    private void handle(List<MapRecord<String, Object, Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            processingTime.record(() -> handleOne(record));
        }
    }

    private void handleOne(MapRecord<String, Object, Object> record) {
        try {
            processor.process(record.getValue());
            acknowledge(record);
            persisted.increment();
        } catch (InvalidEventException e) {
            // Erreur définitive : on isole le message pour ne pas le rejouer sans fin.
            log.warn("Message {} envoyé en dead letter : {}", record.getId(), e.getMessage());
            redis.opsForStream().add(deadLetterStream, stringFields(record.getValue()));
            acknowledge(record);
            deadLettered.increment();
        } catch (RuntimeException e) {
            // Erreur temporaire (Cassandra indisponible) : pas d'ACK, le message reste en attente.
            persistErrors.increment();
            log.error("Persistance échouée pour {}, message conservé : {}", record.getId(), e.getMessage());
            pause(500);
        }
    }

    private Map<String, String> stringFields(Map<Object, Object> fields) {
        Map<String, String> copy = new java.util.HashMap<>();
        fields.forEach((key, value) -> copy.put(String.valueOf(key), String.valueOf(value)));
        return copy;
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        redis.opsForStream().acknowledge(stream, group, record.getId());
    }

    private void ensureGroup() {
        try {
            redis.execute((RedisCallback<String>) connection -> connection.streamCommands().xGroupCreate(
                    stream.getBytes(), group, ReadOffset.from("0"), true));
            log.info("Consumer group {} créé", group);
        } catch (RuntimeException e) {
            if (!String.valueOf(rootMessage(e)).contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    private String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    @Override
    public void start() {
        running = true;
        thread = new Thread(this::run, "stream-consumer");
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
