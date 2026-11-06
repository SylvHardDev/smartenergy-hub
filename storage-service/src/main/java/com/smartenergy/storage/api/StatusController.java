package com.smartenergy.storage.api;

import com.smartenergy.storage.consumer.StreamMonitor;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final StreamMonitor monitor;

    public StatusController(StreamMonitor monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("service", "storage-service", "status", "UP");
    }

    @GetMapping("/api/pipeline/status")
    public Map<String, Long> pipeline() {
        return Map.of("streamLength", monitor.streamLength(), "pendingMessages", monitor.pendingMessages());
    }
}
