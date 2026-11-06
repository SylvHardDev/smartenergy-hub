package com.smartenergy.storage.model;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

public interface MeasurementRepository extends CassandraRepository<MeasurementEntity, MeasurementKey> {

    @Query("SELECT * FROM measurements_by_sensor_day WHERE sensor_id = ?0 AND day = ?1")
    List<MeasurementEntity> findBySensorAndDay(String sensorId, LocalDate day);
}
