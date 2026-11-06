package com.smartenergy.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartenergy.storage.model.DailySummary;
import com.smartenergy.storage.model.MeasurementEntity;
import com.smartenergy.storage.model.MeasurementKey;
import com.smartenergy.storage.service.DailyAggregator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyAggregatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 6);

    private MeasurementEntity at(String time, double power) {
        return new MeasurementEntity(
                new MeasurementKey("S", DAY, Instant.parse("2026-11-06T" + time + "Z")), power, 230.0, null);
    }

    @Test
    void computesIndicatorsWhateverTheInputOrder() {
        DailySummary summary = DailyAggregator.aggregate("S", DAY,
                List.of(at("10:02:00", 6), at("10:00:00", 2), at("10:01:00", 4)), 5.0);

        assertThat(summary.measurementCount()).isEqualTo(3);
        assertThat(summary.avgPowerKw()).isEqualTo(4.0);
        assertThat(summary.minPowerKw()).isEqualTo(2.0);
        assertThat(summary.maxPowerKw()).isEqualTo(6.0);
        assertThat(summary.anomalyCount()).isEqualTo(1);
        // 2 kW pendant 1 min + 4 kW pendant 1 min = 0,1 kWh
        assertThat(summary.energyKwh()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void gapInTheDataAddsNoEnergy() {
        DailySummary summary = DailyAggregator.aggregate("S", DAY,
                List.of(at("10:00:00", 2), at("12:00:00", 2)), null);

        assertThat(summary.energyKwh()).isZero();
    }

    @Test
    void noThresholdMeansNoAnomaly() {
        assertThat(DailyAggregator.aggregate("S", DAY, List.of(at("10:00:00", 100)), null).anomalyCount()).isZero();
    }

    @Test
    void aggregatingTwiceGivesTheSameResult() {
        List<MeasurementEntity> data = List.of(at("10:00:00", 2), at("10:01:00", 4));

        assertThat(DailyAggregator.aggregate("S", DAY, data, 5.0))
                .isEqualTo(DailyAggregator.aggregate("S", DAY, data, 5.0));
    }
}
