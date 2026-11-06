package com.smartenergy.analytics.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
