package com.smartenergy.analytics.api;

import com.smartenergy.analytics.model.Sensor;
import com.smartenergy.analytics.model.SensorRepository;
import com.smartenergy.analytics.service.AnomalyDetector;
import com.smartenergy.analytics.service.Forecaster;
import com.smartenergy.analytics.service.MeasurementQueryService;
import com.smartenergy.analytics.service.Point;
import com.smartenergy.analytics.service.SummaryCalculator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Indicateurs, anomalies et prévision")
public class AnalyticsController {

    private final MeasurementQueryService queries;
    private final SensorRepository sensors;
    private final double zThreshold;
    private final double periodSeconds;
    private final int minPoints;
    private final Duration step;
    private final Timer forecastTimer;

    public AnalyticsController(MeasurementQueryService queries,
                               SensorRepository sensors,
                               MeterRegistry registry,
                               @Value("${smartenergy.z-score-threshold}") double zThreshold,
                               @Value("${smartenergy.forecast.period-seconds}") double periodSeconds,
                               @Value("${smartenergy.forecast.min-points}") int minPoints,
                               @Value("${smartenergy.forecast.step-minutes}") long stepMinutes) {
        this.queries = queries;
        this.sensors = sensors;
        this.zThreshold = zThreshold;
        this.periodSeconds = periodSeconds;
        this.minPoints = minPoints;
        this.step = Duration.ofMinutes(stepMinutes);
        this.forecastTimer = registry.timer("smartenergy.forecast.latency");
    }

    @Operation(summary = "Indicateurs descriptifs", description = "Moyenne, min, max, énergie estimée, nombre de mesures et de dépassements.")
    @GetMapping("/{sensorId}/summary")
    public SummaryCalculator.Summary summary(@PathVariable String sensorId,
                                             @RequestParam(required = false) Instant from,
                                             @RequestParam(required = false) Instant to) {
        return SummaryCalculator.summarize(points(sensorId, from, to), threshold(sensorId));
    }

    @Operation(summary = "Anomalies détectées", description = "Dépassement du seuil du capteur (THRESHOLD) ou |z| supérieur au seuil configuré (Z_SCORE).")
    @GetMapping("/{sensorId}/anomalies")
    public List<AnomalyDetector.Anomaly> anomalies(@PathVariable String sensorId,
                                                   @RequestParam(required = false) Instant from,
                                                   @RequestParam(required = false) Instant to) {
        return AnomalyDetector.detect(points(sensorId, from, to), threshold(sensorId), zThreshold);
    }

    @Operation(summary = "Prévision de consommation", description = "Régression harmonique entraînée sur la période, avec MAE et RMSE mesurées sur les 20 % de points les plus récents.")
    @GetMapping("/{sensorId}/forecast")
    public Forecaster.Forecast forecast(@PathVariable String sensorId,
                                        @RequestParam(required = false) Instant from,
                                        @RequestParam(required = false) Instant to,
                                        @RequestParam(defaultValue = "60") int horizonMinutes) {
        if (horizonMinutes < 1 || horizonMinutes > 1440) {
            throw new BadRequestException("horizonMinutes doit être compris entre 1 et 1440");
        }
        List<Point> points = withoutAnomalies(sensorId, points(sensorId, from, to));
        if (points.size() < minPoints) {
            throw new BadRequestException("Données insuffisantes : " + points.size() + " points, " + minPoints + " requis");
        }
        return forecastTimer.record(() ->
                Forecaster.forecast(points, periodSeconds, Duration.ofMinutes(horizonMinutes), step));
    }

    /** Les pics ne décrivent pas le comportement normal : ils sont retirés de l'apprentissage. */
    private List<Point> withoutAnomalies(String sensorId, List<Point> points) {
        var anomalous = AnomalyDetector.detect(points, threshold(sensorId), zThreshold).stream()
                .map(AnomalyDetector.Anomaly::at)
                .collect(java.util.stream.Collectors.toSet());
        return points.stream().filter(point -> !anomalous.contains(point.at())).toList();
    }

    private List<Point> points(String sensorId, Instant from, Instant to) {
        return queries.points(sensorId, queries.range(from, to));
    }

    private Double threshold(String sensorId) {
        return sensors.findById(sensorId).map(Sensor::maxPowerKw).orElse(null);
    }
}
