package com.smartenergy.analytics.api;

import com.smartenergy.analytics.model.DailySummary;
import com.smartenergy.analytics.model.DailySummaryRepository;
import com.smartenergy.analytics.model.Sensor;
import com.smartenergy.analytics.model.SensorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sensors")
@Tag(name = "Capteurs", description = "Métadonnées stockées dans MongoDB")
public class SensorController {

    private final SensorRepository sensors;
    private final DailySummaryRepository summaries;
    private final Clock clock;

    public SensorController(SensorRepository sensors, DailySummaryRepository summaries, Clock clock) {
        this.sensors = sensors;
        this.summaries = summaries;
        this.clock = clock;
    }

    @Operation(summary = "Créer un capteur")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Sensor create(@Valid @RequestBody Sensor sensor) {
        if (sensors.existsById(sensor.sensorId())) {
            throw new ConflictException("Le capteur " + sensor.sensorId() + " existe déjà");
        }
        return sensors.save(sensor);
    }

    @Operation(summary = "Lister les capteurs")
    @GetMapping
    public List<Sensor> list() {
        return sensors.findAll();
    }

    @Operation(summary = "Afficher un capteur")
    @GetMapping("/{id}")
    public Sensor get(@PathVariable String id) {
        return sensors.findById(id).orElseThrow(() -> new NotFoundException("Capteur inconnu : " + id));
    }

    @Operation(summary = "Modifier un capteur")
    @PutMapping("/{id}")
    public Sensor update(@PathVariable String id, @Valid @RequestBody Sensor sensor) {
        if (!sensors.existsById(id)) {
            throw new NotFoundException("Capteur inconnu : " + id);
        }
        return sensors.save(sensor.withId(id));
    }

    @Operation(summary = "Résumé quotidien pré-calculé", description = "Lit l'agrégat MongoDB. Sans date : jour UTC courant.")
    @GetMapping("/{id}/daily-summary")
    public DailySummary dailySummary(@PathVariable String id,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return summaries.findById(id + "|" + day)
                .orElseThrow(() -> new NotFoundException("Aucun résumé pour " + id + " le " + day));
    }
}
