package com.smartenergy.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartenergy.analytics.service.Forecaster;
import com.smartenergy.analytics.service.Point;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForecasterTest {

    private static final double PERIOD = 3600;

    private double signal(long seconds) {
        return 4 + 1.5 * Math.sin(2 * Math.PI * seconds / PERIOD);
    }

    @Test
    void recoversAPeriodicSignal() {
        Instant start = Instant.parse("2026-11-06T00:00:00Z");
        List<Point> points = new ArrayList<>();
        for (long s = 0; s < 6 * 3600; s += 60) {
            points.add(new Point(start.plusSeconds(s), signal(s)));
        }

        Forecaster.Forecast forecast = Forecaster.forecast(points, PERIOD, Duration.ofMinutes(60), Duration.ofMinutes(5));

        assertThat(forecast.mae()).isLessThan(0.01);
        assertThat(forecast.rmse()).isLessThan(0.01);
        assertThat(forecast.predictions()).hasSize(12);
        Forecaster.ForecastPoint first = forecast.predictions().get(0);
        long seconds = Duration.between(start, first.at()).toSeconds();
        assertThat(first.powerKw()).isCloseTo(signal(seconds), within(0.01));
    }

    @Test
    void trainTestSplitIsEightyTwenty() {
        Instant start = Instant.parse("2026-11-06T00:00:00Z");
        List<Point> points = new ArrayList<>();
        for (long s = 0; s < 100 * 60; s += 60) {
            points.add(new Point(start.plusSeconds(s), signal(s)));
        }

        Forecaster.Forecast forecast = Forecaster.forecast(points, PERIOD, Duration.ofMinutes(10), Duration.ofMinutes(5));

        assertThat(forecast.trainPoints()).isEqualTo(80);
        assertThat(forecast.testPoints()).isEqualTo(20);
    }
}
