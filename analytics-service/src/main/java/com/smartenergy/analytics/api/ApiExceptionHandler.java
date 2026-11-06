package com.smartenergy.analytics.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .sorted()
                .toList();
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", "Requête invalide", "details", details));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "Paramètre " + e.getName() + " invalide (dates au format ISO-8601 UTC)");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "JSON illisible");
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Map<String, Object>> badRequest(BadRequestException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
