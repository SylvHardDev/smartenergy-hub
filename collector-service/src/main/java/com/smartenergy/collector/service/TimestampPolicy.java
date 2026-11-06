package com.smartenergy.collector.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Refuse les mesures trop anciennes ou datées dans le futur (horloge de capteur déréglée). */
@Component
public class TimestampPolicy {

    private final Clock clock;
    private final Duration maxAge;
    private final Duration maxFuture;

    public TimestampPolicy(Clock clock,
                           @Value("${smartenergy.max-age-days}") long maxAgeDays,
                           @Value("${smartenergy.max-future-minutes}") long maxFutureMinutes) {
        this.clock = clock;
        this.maxAge = Duration.ofDays(maxAgeDays);
        this.maxFuture = Duration.ofMinutes(maxFutureMinutes);
    }

    public void check(Instant timestamp) {
        Instant now = clock.instant();
        if (timestamp.isAfter(now.plus(maxFuture))) {
            throw new InvalidMeasurementException("timestamp est dans le futur");
        }
        if (timestamp.isBefore(now.minus(maxAge))) {
            throw new InvalidMeasurementException("timestamp est trop ancien (plus de " + maxAge.toDays() + " jours)");
        }
    }
}
