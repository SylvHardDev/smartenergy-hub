package com.smartenergy.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartenergy.analytics.service.AnomalyDetector;
import com.smartenergy.analytics.service.Point;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnomalyDetectorTest {

    private List<Point> steadyWithSpike(double spike) {
        List<Point> points = new ArrayList<>();
        Instant start = Instant.parse("2026-11-06T10:00:00Z");
        for (int i = 0; i < 100; i++) {
            points.add(new Point(start.plusSeconds(i), i == 50 ? spike : 3 + (i % 2) * 0.2));
        }
        return points;
    }

    @Test
    void spikeOverTheThresholdIsReportedForBothReasons() {
        List<AnomalyDetector.Anomaly> anomalies = AnomalyDetector.detect(steadyWithSpike(12), 8.0, 3.0);

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).powerKw()).isEqualTo(12);
        assertThat(anomalies.get(0).reasons()).containsExactly("THRESHOLD", "Z_SCORE");
    }

    @Test
    void zScoreFindsASpikeBelowTheThreshold() {
        List<AnomalyDetector.Anomaly> anomalies = AnomalyDetector.detect(steadyWithSpike(7), 8.0, 3.0);

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).reasons()).containsExactly("Z_SCORE");
    }

    @Test
    void constantSignalHasNoAnomaly() {
        List<Point> points = List.of(
                new Point(Instant.parse("2026-11-06T10:00:00Z"), 3),
                new Point(Instant.parse("2026-11-06T10:00:01Z"), 3));

        assertThat(AnomalyDetector.detect(points, 8.0, 3.0)).isEmpty();
    }
}
