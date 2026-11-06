package com.smartenergy.collector.api;

import com.smartenergy.collector.service.MeasurementPublisher;
import com.smartenergy.collector.service.TimestampPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/measurements")
public class MeasurementController {

    private final MeasurementPublisher publisher;
    private final TimestampPolicy timestampPolicy;

    public MeasurementController(MeasurementPublisher publisher, TimestampPolicy timestampPolicy) {
        this.publisher = publisher;
        this.timestampPolicy = timestampPolicy;
    }

    @Operation(summary = "Recevoir une mesure",
            description = "Valide la mesure et la publie dans Redis Streams. Le 202 signifie « accepté pour traitement », pas « stocké ».")
    @ApiResponse(responseCode = "202", description = "Mesure publiée dans le stream")
    @ApiResponse(responseCode = "400", description = "Mesure invalide, rien n'est publié")
    @ApiResponse(responseCode = "503", description = "Redis indisponible, l'émetteur doit réessayer")
    @PostMapping
    public ResponseEntity<Void> receive(@Valid @RequestBody EnergyMeasurementRequest request) {
        timestampPolicy.check(request.timestamp());
        publisher.publish(request);
        return ResponseEntity.accepted().build();
    }
}
