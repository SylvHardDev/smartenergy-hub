package com.smartenergy.storage.model;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DailySummaryRepository extends MongoRepository<DailySummary, String> {
}
