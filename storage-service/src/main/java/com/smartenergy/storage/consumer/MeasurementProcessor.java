package com.smartenergy.storage.consumer;

import com.smartenergy.storage.model.MeasurementEntity;
import com.smartenergy.storage.model.MeasurementRepository;
import com.smartenergy.storage.service.DailyAggregationService;
import com.smartenergy.storage.service.LatestMeasurementCache;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MeasurementProcessor {

    private final MeasurementRepository repository;
    private final LatestMeasurementCache cache;
    private final DailyAggregationService aggregation;

    public MeasurementProcessor(MeasurementRepository repository,
                                LatestMeasurementCache cache,
                                DailyAggregationService aggregation) {
        this.repository = repository;
        this.cache = cache;
        this.aggregation = aggregation;
    }

    /**
     * Idempotent : la clé primaire Cassandra (capteur, jour, instant) transforme un rejeu en simple
     * réécriture, et l'agrégat est recalculé depuis Cassandra plutôt qu'incrémenté.
     */
    public void process(Map<?, ?> fields) {
        MeasurementEntity entity = MeasurementMapper.toEntity(fields);
        repository.save(entity);
        cache.update(entity);
        aggregation.markDirty(entity.getKey().getSensorId(), entity.getKey().getDay());
    }
}
