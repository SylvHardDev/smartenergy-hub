package com.smartenergy.storage.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Vue réduite d'un capteur : le Storage Service n'a besoin que du seuil. */
@Document("sensors")
public record SensorDocument(@Id String sensorId, Double maxPowerKw) {
}
