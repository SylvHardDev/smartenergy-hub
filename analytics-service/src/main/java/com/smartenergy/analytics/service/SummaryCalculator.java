package com.smartenergy.analytics.service;

import java.time.Duration;
import java.util.List;

/** Niveau A : indicateurs descriptifs. Les points doivent être triés par date croissante. */
public final class SummaryCalculator {

    /** Au-delà de cet écart, on considère un trou dans les données plutôt qu'une consommation continue. */
    static final Duration MAX_GAP = Duration.ofMinutes(5);

    public record Summary(long measurementCount, double avgPowerKw, double minPowerKw, double maxPowerKw,
                          double energyKwh, long overThresholdCount) {
    }

    private SummaryCalculator() {
    }

    public static Summary summarize(List<Point> points, Double thresholdKw) {
        if (points.isEmpty()) {
            return new Summary(0, 0, 0, 0, 0, 0);
        }
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        long over = 0;
        for (Point point : points) {
            sum += point.powerKw();
            min = Math.min(min, point.powerKw());
            max = Math.max(max, point.powerKw());
            if (thresholdKw != null && point.powerKw() > thresholdKw) {
                over++;
            }
        }
        return new Summary(points.size(), sum / points.size(), min, max, energyKwh(points), over);
    }

    /** E = somme(Pi * dti), avec dti l'écart réel entre deux mesures, en heures. */
    static double energyKwh(List<Point> points) {
        double energy = 0;
        for (int i = 1; i < points.size(); i++) {
            Duration gap = Duration.between(points.get(i - 1).at(), points.get(i).at());
            if (gap.compareTo(MAX_GAP) <= 0) {
                energy += points.get(i - 1).powerKw() * gap.toMillis() / 3_600_000.0;
            }
        }
        return energy;
    }
}
