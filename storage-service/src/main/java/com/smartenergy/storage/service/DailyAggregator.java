package com.smartenergy.storage.service;

import com.smartenergy.storage.model.DailySummary;
import com.smartenergy.storage.model.MeasurementEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Calcul pur de l'agrégat d'une journée, sans accès aux bases. */
public final class DailyAggregator {

    /** Au-delà de cet écart, on considère un trou dans les données plutôt qu'une consommation continue. */
    static final Duration MAX_GAP = Duration.ofMinutes(5);

    private DailyAggregator() {
    }

    public static DailySummary aggregate(String sensorId, LocalDate day, List<MeasurementEntity> measurements, Double maxPowerKw) {
        List<MeasurementEntity> ordered = measurements.stream()
                .sorted(Comparator.comparing(m -> m.getKey().getMeasuredAt()))
                .toList();
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double energy = 0;
        long anomalies = 0;
        Instant previous = null;
        double previousPower = 0;
        for (MeasurementEntity m : ordered) {
            double power = m.getPowerKw();
            sum += power;
            min = Math.min(min, power);
            max = Math.max(max, power);
            if (maxPowerKw != null && power > maxPowerKw) {
                anomalies++;
            }
            Instant at = m.getKey().getMeasuredAt();
            if (previous != null) {
                Duration gap = Duration.between(previous, at);
                if (gap.compareTo(MAX_GAP) <= 0) {
                    energy += previousPower * gap.toMillis() / 3_600_000.0;
                }
            }
            previous = at;
            previousPower = power;
        }
        int count = ordered.size();
        return new DailySummary(sensorId + "|" + day, sensorId, day.toString(), count,
                count == 0 ? 0 : sum / count,
                count == 0 ? 0 : min,
                count == 0 ? 0 : max,
                energy,
                anomalies);
    }
}
