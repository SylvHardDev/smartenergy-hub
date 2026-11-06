package com.smartenergy.analytics.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

public interface MeasurementRepository extends CassandraRepository<MeasurementEntity, MeasurementKey> {

    @Query("SELECT * FROM measurements_by_sensor_day WHERE sensor_id = ?0 AND day = ?1 AND measured_at >= ?2 AND measured_at <= ?3")
    List<MeasurementEntity> findRange(String sensorId, LocalDate day, Instant from, Instant to);

    @Query("SELECT * FROM measurements_by_sensor_day WHERE sensor_id = ?0 AND day = ?1 LIMIT 1")
    List<MeasurementEntity> findLatestOfDay(String sensorId, LocalDate day);
}
