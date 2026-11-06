package com.smartenergy.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartenergy.storage.consumer.InvalidEventException;
import com.smartenergy.storage.consumer.MeasurementMapper;
import com.smartenergy.storage.model.MeasurementEntity;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MeasurementMapperTest {

    @Test
    void mapsEventToEntityUsingTheUtcDay() {
        MeasurementEntity entity = MeasurementMapper.toEntity(Map.of(
                "sensorId", "SENSOR-001",
                "timestamp", "2026-11-06T23:59:59Z",
                "powerKw", "3.72",
                "voltage", "226.4",
                "temperature", "29.1"));

        assertThat(entity.getKey().getSensorId()).isEqualTo("SENSOR-001");
        assertThat(entity.getKey().getDay()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(entity.getPowerKw()).isEqualTo(3.72);
        assertThat(entity.getTemperature()).isEqualTo(29.1);
    }

    @Test
    void temperatureIsOptional() {
        MeasurementEntity entity = MeasurementMapper.toEntity(Map.of(
                "sensorId", "SENSOR-001", "timestamp", "2026-11-06T10:00:00Z", "powerKw", "1", "voltage", "230"));

        assertThat(entity.getTemperature()).isNull();
    }

    @Test
    void missingFieldIsADefinitiveError() {
        assertThatThrownBy(() -> MeasurementMapper.toEntity(Map.of("sensorId", "SENSOR-001")))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void nonNumericPowerIsADefinitiveError() {
        assertThatThrownBy(() -> MeasurementMapper.toEntity(Map.of(
                "sensorId", "SENSOR-001", "timestamp", "2026-11-06T10:00:00Z", "powerKw", "abc", "voltage", "230")))
                .isInstanceOf(InvalidEventException.class);
    }
}
