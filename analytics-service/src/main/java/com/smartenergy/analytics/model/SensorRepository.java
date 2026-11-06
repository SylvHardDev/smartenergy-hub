package com.smartenergy.analytics.model;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SensorRepository extends MongoRepository<Sensor, String> {
}
