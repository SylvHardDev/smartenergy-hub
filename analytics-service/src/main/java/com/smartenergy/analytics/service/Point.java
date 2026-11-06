package com.smartenergy.analytics.service;

import java.time.Instant;

/** Mesure réduite à ce dont les calculs ont besoin. */
public record Point(Instant at, double powerKw) {
}
