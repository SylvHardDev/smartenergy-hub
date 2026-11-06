package com.smartenergy.collector.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

/** Contrat de données d'une mesure. Ces règles couvrent le format, pas les anomalies énergétiques. */
public record EnergyMeasurementRequest(
        @Schema(example = "SENSOR-001")
        @NotBlank(message = "sensorId est obligatoire")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "sensorId : 1 à 64 caractères alphanumériques, - ou _")
        String sensorId,

        @Schema(example = "2026-11-06T08:15:00Z")
        @NotNull(message = "timestamp est obligatoire")
        Instant timestamp,

        @Schema(example = "3.72")
        @NotNull(message = "powerKw est obligatoire")
        @PositiveOrZero(message = "powerKw doit être positif ou nul")
        Double powerKw,

        @Schema(example = "226.4")
        @NotNull(message = "voltage est obligatoire")
        @DecimalMin(value = "80.0", message = "voltage doit être supérieur ou égal à 80 V")
        @DecimalMax(value = "480.0", message = "voltage doit être inférieur ou égal à 480 V")
        Double voltage,

        @Schema(example = "29.1")
        @DecimalMin(value = "-50.0", message = "temperature doit être supérieure ou égale à -50 °C")
        @DecimalMax(value = "100.0", message = "temperature doit être inférieure ou égale à 100 °C")
        Double temperature) {
}
