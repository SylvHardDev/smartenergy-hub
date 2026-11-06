package com.smartenergy.analytics.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

@PrimaryKeyClass
public class MeasurementKey implements Serializable {

    @PrimaryKeyColumn(name = "sensor_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    private String sensorId;

    @PrimaryKeyColumn(name = "day", type = PrimaryKeyType.PARTITIONED, ordinal = 1)
    private LocalDate day;

    @PrimaryKeyColumn(name = "measured_at", type = PrimaryKeyType.CLUSTERED, ordinal = 2, ordering = Ordering.DESCENDING)
    private Instant measuredAt;

    public MeasurementKey() {
    }

    public String getSensorId() {
        return sensorId;
    }

    public LocalDate getDay() {
        return day;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MeasurementKey key
                && Objects.equals(sensorId, key.sensorId)
                && Objects.equals(day, key.day)
                && Objects.equals(measuredAt, key.measuredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sensorId, day, measuredAt);
    }
}
