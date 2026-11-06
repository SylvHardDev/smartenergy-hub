package com.smartenergy.storage.consumer;

import com.smartenergy.storage.model.MeasurementEntity;
import com.smartenergy.storage.model.MeasurementKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/** Transforme les champs d'un événement Redis en entité Cassandra. */
public final class MeasurementMapper {

    private MeasurementMapper() {
    }

    public static MeasurementEntity toEntity(Map<?, ?> fields) {
        try {
            String sensorId = required(fields, "sensorId");
            Instant measuredAt = Instant.parse(required(fields, "timestamp"));
            LocalDate day = LocalDate.ofInstant(measuredAt, ZoneOffset.UTC);
            Object temperature = fields.get("temperature");
            return new MeasurementEntity(
                    new MeasurementKey(sensorId, day, measuredAt),
                    Double.valueOf(required(fields, "powerKw")),
                    Double.valueOf(required(fields, "voltage")),
                    temperature == null ? null : Double.valueOf(temperature.toString()));
        } catch (RuntimeException e) {
            throw new InvalidEventException("Événement invalide : " + fields, e);
        }
    }

    private static String required(Map<?, ?> fields, String name) {
        Object value = fields.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Champ manquant : " + name);
        }
        return value.toString();
    }
}
