package com.smartenergy.analytics.api;

import com.smartenergy.analytics.service.MeasurementQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/measurements")
@Tag(name = "Mesures", description = "Séries temporelles stockées dans Cassandra")
public class MeasurementQueryController {

    private final MeasurementQueryService queries;

    public MeasurementQueryController(MeasurementQueryService queries) {
        this.queries = queries;
    }

    @Operation(summary = "Historique d'un capteur", description = "Du plus récent au plus ancien. Par défaut : les dernières 24 h, 31 jours maximum.")
    @GetMapping("/{sensorId}")
    public List<MeasurementView> history(@PathVariable String sensorId,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        return queries.history(sensorId, queries.range(from, to)).stream()
                .map(entity -> MeasurementView.from(entity, "cassandra"))
                .toList();
    }

    @Operation(summary = "Dernière mesure connue", description = "Servie par le cache Redis, avec repli sur Cassandra.")
    @GetMapping("/{sensorId}/latest")
    public MeasurementView latest(@PathVariable String sensorId) {
        return queries.latest(sensorId)
                .orElseThrow(() -> new NotFoundException("Aucune mesure pour " + sensorId));
    }
}
