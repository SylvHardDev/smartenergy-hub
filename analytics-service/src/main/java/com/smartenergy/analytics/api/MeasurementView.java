package com.smartenergy.analytics.api;

import com.smartenergy.analytics.model.MeasurementEntity;
import java.time.Instant;

/** Mesure telle qu'exposée par l'API. source indique la base qui a répondu. */
public record MeasurementView(String sensorId, Instant timestamp, Double powerKw, Double voltage,
                              Double temperature, String source) {

    public static MeasurementView from(MeasurementEntity entity, String source) {
        return new MeasurementView(entity.getKey().getSensorId(), entity.getKey().getMeasuredAt(),
                entity.getPowerKw(), entity.getVoltage(), entity.getTemperature(), source);
    }
}
