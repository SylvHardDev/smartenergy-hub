package com.smartenergy.storage.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Agrégat quotidien, identifié par capteur et date : le réécrire remplace la version précédente. */
@Document("daily_summaries")
public record DailySummary(
        @Id String id,
        String sensorId,
        String date,
        long measurementCount,
        double avgPowerKw,
        double minPowerKw,
        double maxPowerKw,
        double energyKwh,
        long anomalyCount) {
}
