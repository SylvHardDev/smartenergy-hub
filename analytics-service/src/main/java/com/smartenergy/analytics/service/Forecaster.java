package com.smartenergy.analytics.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Niveau C : régression harmonique par moindres carrés.
 * P(t) = b0 + b1*t + b2*sin(2*pi*t/T) + b3*cos(2*pi*t/T)
 * L'évaluation est chronologique : 80 % des points pour l'entraînement, les 20 % les plus récents pour
 * le test. Un découpage aléatoire laisserait le modèle « voir le futur ».
 */
public final class Forecaster {

    public record ForecastPoint(Instant at, double powerKw) {
    }

    public record Forecast(String model, int trainPoints, int testPoints, double mae, double rmse,
                           List<ForecastPoint> predictions) {
    }

    private Forecaster() {
    }

    public static Forecast forecast(List<Point> points, double periodSeconds, Duration horizon, Duration step) {
        int split = (int) (points.size() * 0.8);
        List<Point> train = points.subList(0, split);
        List<Point> test = points.subList(split, points.size());
        Instant origin = points.get(0).at();

        double[] evaluationModel = fit(train, origin, periodSeconds);
        double absolute = 0;
        double squared = 0;
        for (Point point : test) {
            double error = point.powerKw() - predict(evaluationModel, point.at(), origin, periodSeconds);
            absolute += Math.abs(error);
            squared += error * error;
        }

        // Le modèle final utilise toutes les données disponibles.
        double[] model = fit(points, origin, periodSeconds);
        Instant last = points.get(points.size() - 1).at();
        List<ForecastPoint> predictions = new ArrayList<>();
        for (Instant at = last.plus(step); !at.isAfter(last.plus(horizon)); at = at.plus(step)) {
            predictions.add(new ForecastPoint(at, Math.max(0, predict(model, at, origin, periodSeconds))));
        }
        return new Forecast("harmonic-regression", train.size(), test.size(),
                absolute / test.size(), Math.sqrt(squared / test.size()), predictions);
    }

    private static double[] features(Instant at, Instant origin, double periodSeconds) {
        double seconds = Duration.between(origin, at).toMillis() / 1000.0;
        double angle = 2 * Math.PI * seconds / periodSeconds;
        return new double[] {1, seconds / periodSeconds, Math.sin(angle), Math.cos(angle)};
    }

    private static double predict(double[] coefficients, Instant at, Instant origin, double periodSeconds) {
        double[] x = features(at, origin, periodSeconds);
        double value = 0;
        for (int i = 0; i < x.length; i++) {
            value += coefficients[i] * x[i];
        }
        return value;
    }

    /** Équations normales (X'X) b = X'y. */
    private static double[] fit(List<Point> points, Instant origin, double periodSeconds) {
        int n = 4;
        double[][] a = new double[n][n + 1];
        for (Point point : points) {
            double[] x = features(point.at(), origin, periodSeconds);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    a[i][j] += x[i] * x[j];
                }
                a[i][n] += x[i] * point.powerKw();
            }
        }
        for (int i = 0; i < n; i++) {
            a[i][i] += 1e-9; // évite une matrice singulière si la fenêtre couvre très peu de temps
        }
        return solve(a);
    }

    /** Élimination de Gauss avec pivot partiel. */
    private static double[] solve(double[][] a) {
        int n = a.length;
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }
            double[] swap = a[col];
            a[col] = a[pivot];
            a[pivot] = swap;
            for (int row = col + 1; row < n; row++) {
                double factor = a[row][col] / a[col][col];
                for (int k = col; k <= n; k++) {
                    a[row][k] -= factor * a[col][k];
                }
            }
        }
        double[] solution = new double[n];
        for (int row = n - 1; row >= 0; row--) {
            double value = a[row][n];
            for (int k = row + 1; k < n; k++) {
                value -= a[row][k] * solution[k];
            }
            solution[row] = value / a[row][row];
        }
        return solution;
    }
}
