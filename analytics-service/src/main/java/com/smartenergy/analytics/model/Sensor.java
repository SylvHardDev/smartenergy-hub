package com.smartenergy.analytics.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("sensors")
public record Sensor(
        @Id
        @Schema(example = "SENSOR-006")
        @NotBlank(message = "sensorId est obligatoire")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "sensorId : 1 à 64 caractères alphanumériques, - ou _")
        String sensorId,
        @Schema(example = "Compteur cafétéria") @NotBlank(message = "name est obligatoire") String name,
        @Schema(example = "Bâtiment A") String building,
        @Schema(example = "1") Integer floor,
        @Schema(example = "8.0") @NotNull(message = "maxPowerKw est obligatoire") @Positive(message = "maxPowerKw doit être positif") Double maxPowerKw,
        @Schema(example = "true") boolean active,
        List<String> tags) {

    public Sensor withId(String id) {
        return new Sensor(id, name, building, floor, maxPowerKw, active, tags);
    }
}
