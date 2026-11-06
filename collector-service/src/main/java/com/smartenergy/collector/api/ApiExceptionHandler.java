package com.smartenergy.collector.api;

import com.smartenergy.collector.service.InvalidMeasurementException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Counter rejected;

    public ApiExceptionHandler(MeterRegistry registry) {
        this.rejected = registry.counter("smartenergy.measurements.rejected");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidFields(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .sorted()
                .toList();
        return badRequest(details);
    }

    @ExceptionHandler(InvalidMeasurementException.class)
    ResponseEntity<Map<String, Object>> invalidMeasurement(InvalidMeasurementException e) {
        return badRequest(List.of(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return badRequest(List.of("JSON illisible ou type de champ incorrect (timestamp attendu au format ISO-8601 UTC)"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> redisUnavailable(DataAccessException e) {
        log.error("Publication Redis impossible : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503, "error", "Service temporairement indisponible, réessayez"));
    }

    private ResponseEntity<Map<String, Object>> badRequest(List<String> details) {
        rejected.increment();
        return ResponseEntity.badRequest()
                .body(Map.of("status", 400, "error", "Mesure invalide", "details", details));
    }
}
