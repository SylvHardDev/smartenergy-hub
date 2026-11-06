package com.smartenergy.storage.service;

import com.smartenergy.storage.model.DailySummaryRepository;
import com.smartenergy.storage.model.MeasurementRepository;
import com.smartenergy.storage.model.SensorDocument;
import com.smartenergy.storage.model.SensorRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Recalcule les agrégats depuis Cassandra, source de vérité. Un compteur incrémenté compterait
 * deux fois un message rejoué ; un recalcul donne toujours le même résultat.
 */
@Service
public class DailyAggregationService {

    private static final Logger log = LoggerFactory.getLogger(DailyAggregationService.class);

    private record SensorDay(String sensorId, LocalDate day) {
    }

    private final Set<SensorDay> dirty = ConcurrentHashMap.newKeySet();
    private final MeasurementRepository measurements;
    private final DailySummaryRepository summaries;
    private final SensorRepository sensors;

    public DailyAggregationService(MeasurementRepository measurements,
                                   DailySummaryRepository summaries,
                                   SensorRepository sensors) {
        this.measurements = measurements;
        this.summaries = summaries;
        this.sensors = sensors;
    }

    public void markDirty(String sensorId, LocalDate day) {
        dirty.add(new SensorDay(sensorId, day));
    }

    @Scheduled(fixedDelayString = "${smartenergy.aggregate-interval-ms}")
    public void refresh() {
        for (SensorDay target : List.copyOf(dirty)) {
            dirty.remove(target);
            try {
                Double threshold = sensors.findById(target.sensorId()).map(SensorDocument::maxPowerKw).orElse(null);
                summaries.save(DailyAggregator.aggregate(target.sensorId(), target.day(),
                        measurements.findBySensorAndDay(target.sensorId(), target.day()), threshold));
            } catch (RuntimeException e) {
                dirty.add(target);
                log.error("Agrégat non mis à jour pour {} : {}", target, e.getMessage());
            }
        }
    }
}
