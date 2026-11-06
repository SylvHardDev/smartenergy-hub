package com.smartenergy.analytics.model;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("measurements_by_sensor_day")
public class MeasurementEntity {

    @PrimaryKey
    private MeasurementKey key;

    @Column("power_kw")
    private Double powerKw;

    @Column("voltage")
    private Double voltage;

    @Column("temperature")
    private Double temperature;

    public MeasurementKey getKey() {
        return key;
    }

    public Double getPowerKw() {
        return powerKw;
    }

    public Double getVoltage() {
        return voltage;
    }

    public Double getTemperature() {
        return temperature;
    }
}
