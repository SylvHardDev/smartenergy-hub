package com.smartenergy.analytics.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Niveau B : dépassement du seuil du capteur et score standardisé z = (P - mu) / sigma. */
public final class AnomalyDetector {

    public record Anomaly(Instant at, double powerKw, double zScore, List<String> reasons) {
    }

    private AnomalyDetector() {
    }

    public static List<Anomaly> detect(List<Point> points, Double thresholdKw, double zThreshold) {
        double mean = points.stream().mapToDouble(Point::powerKw).average().orElse(0);
        double variance = points.stream().mapToDouble(p -> Math.pow(p.powerKw() - mean, 2)).average().orElse(0);
        double sigma = Math.sqrt(variance);

        List<Anomaly> anomalies = new ArrayList<>();
        for (Point point : points) {
            double z = sigma == 0 ? 0 : (point.powerKw() - mean) / sigma;
            List<String> reasons = new ArrayList<>();
            if (thresholdKw != null && point.powerKw() > thresholdKw) {
                reasons.add("THRESHOLD");
            }
            if (Math.abs(z) > zThreshold) {
                reasons.add("Z_SCORE");
            }
            if (!reasons.isEmpty()) {
                anomalies.add(new Anomaly(point.at(), point.powerKw(), z, reasons));
            }
        }
        return anomalies;
    }
}
