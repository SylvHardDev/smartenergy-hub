package com.smartenergy.analytics.service;

import com.smartenergy.analytics.api.BadRequestException;
import com.smartenergy.analytics.api.MeasurementView;
import com.smartenergy.analytics.model.MeasurementEntity;
import com.smartenergy.analytics.model.MeasurementRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MeasurementQueryService {

    private static final Logger log = LoggerFactory.getLogger(MeasurementQueryService.class);

    public record Range(Instant from, Instant to) {
    }

    private final MeasurementRepository repository;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Duration maxRange;
    private final Duration defaultRange;

    public MeasurementQueryService(MeasurementRepository repository,
                                   StringRedisTemplate redis,
                                   Clock clock,
                                   @Value("${smartenergy.max-range-days}") long maxRangeDays,
                                   @Value("${smartenergy.default-range-hours}") long defaultRangeHours) {
        this.repository = repository;
        this.redis = redis;
        this.clock = clock;
        this.maxRange = Duration.ofDays(maxRangeDays);
        this.defaultRange = Duration.ofHours(defaultRangeHours);
    }

    public Range range(Instant from, Instant to) {
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(defaultRange);
        if (start.isAfter(end)) {
            throw new BadRequestException("from doit être antérieur à to");
        }
        if (Duration.between(start, end).compareTo(maxRange) > 0) {
            throw new BadRequestException("La période demandée dépasse " + maxRange.toDays() + " jours");
        }
        return new Range(start, end);
    }

    /** Une requête par partition (capteur, jour) : on ne fait jamais de scan de table. */
    public List<MeasurementEntity> history(String sensorId, Range range) {
        LocalDate first = LocalDate.ofInstant(range.from(), ZoneOffset.UTC);
        LocalDate last = LocalDate.ofInstant(range.to(), ZoneOffset.UTC);
        List<MeasurementEntity> result = new ArrayList<>();
        for (LocalDate day = last; !day.isBefore(first); day = day.minusDays(1)) {
            result.addAll(repository.findRange(sensorId, day, range.from(), range.to()));
        }
        return result; // du plus récent au plus ancien, grâce au clustering DESC
    }

    public List<Point> points(String sensorId, Range range) {
        return history(sensorId, range).stream()
                .map(m -> new Point(m.getKey().getMeasuredAt(), m.getPowerKw()))
                .sorted(Comparator.comparing(Point::at))
                .toList();
    }

    /** Lecture depuis le cache Redis, puis repli sur Cassandra (aujourd'hui, sinon les 7 jours précédents). */
    public Optional<MeasurementView> latest(String sensorId) {
        Optional<MeasurementView> cached = fromCache(sensorId);
        if (cached.isPresent()) {
            return cached;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (int i = 0; i <= 7; i++) {
            List<MeasurementEntity> rows = repository.findLatestOfDay(sensorId, today.minusDays(i));
            if (!rows.isEmpty()) {
                return Optional.of(MeasurementView.from(rows.get(0), "cassandra"));
            }
        }
        return Optional.empty();
    }

    private Optional<MeasurementView> fromCache(String sensorId) {
        try {
            Map<Object, Object> fields = redis.opsForHash().entries("latest:" + sensorId);
            if (fields.isEmpty()) {
                return Optional.empty();
            }
            Object temperature = fields.get("temperature");
            return Optional.of(new MeasurementView(sensorId,
                    Instant.parse(fields.get("timestamp").toString()),
                    Double.valueOf(fields.get("powerKw").toString()),
                    Double.valueOf(fields.get("voltage").toString()),
                    temperature == null ? null : Double.valueOf(temperature.toString()),
                    "redis"));
        } catch (RuntimeException e) {
            log.warn("Cache Redis indisponible : {}", e.getMessage());
            return Optional.empty();
        }
    }
}
