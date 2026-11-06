package com.smartenergy.storage.model;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SensorRepository extends MongoRepository<SensorDocument, String> {
}
