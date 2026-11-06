package com.smartenergy.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartenergy.analytics.service.Point;
import com.smartenergy.analytics.service.SummaryCalculator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryCalculatorTest {

    private Point at(String time, double power) {
        return new Point(Instant.parse("2026-11-06T" + time + "Z"), power);
    }

    @Test
    void computesAllIndicators() {
        SummaryCalculator.Summary summary = SummaryCalculator.summarize(
                List.of(at("10:00:00", 2), at("10:01:00", 4), at("10:02:00", 6)), 5.0);

        assertThat(summary.measurementCount()).isEqualTo(3);
        assertThat(summary.avgPowerKw()).isEqualTo(4.0);
        assertThat(summary.minPowerKw()).isEqualTo(2.0);
        assertThat(summary.maxPowerKw()).isEqualTo(6.0);
        assertThat(summary.overThresholdCount()).isEqualTo(1);
        assertThat(summary.energyKwh()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void oneHourAtTwoKwIsTwoKwh() {
        SummaryCalculator.Summary summary = SummaryCalculator.summarize(
                java.util.stream.IntStream.rangeClosed(0, 60)
                        .mapToObj(minute -> new Point(Instant.parse("2026-11-06T10:00:00Z").plusSeconds(60L * minute), 2))
                        .toList(), null);

        assertThat(summary.energyKwh()).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void gapInTheDataAddsNoEnergy() {
        assertThat(SummaryCalculator.summarize(List.of(at("10:00:00", 2), at("12:00:00", 2)), null).energyKwh()).isZero();
    }

    @Test
    void noDataGivesAnEmptySummary() {
        assertThat(SummaryCalculator.summarize(List.of(), 5.0).measurementCount()).isZero();
    }
}
