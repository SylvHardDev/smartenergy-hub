package com.smartenergy.collector.service;

public class InvalidMeasurementException extends RuntimeException {

    public InvalidMeasurementException(String message) {
        super(message);
    }
}
