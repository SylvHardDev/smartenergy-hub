package com.smartenergy.collector;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartenergy.collector.api.ApiExceptionHandler;
import com.smartenergy.collector.api.MeasurementController;
import com.smartenergy.collector.service.MeasurementPublisher;
import com.smartenergy.collector.service.TimestampPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(MeasurementController.class)
@Import({ApiExceptionHandler.class, TimestampPolicy.class, MeasurementControllerTest.Config.class})
@TestPropertySource(properties = {"smartenergy.max-age-days=7", "smartenergy.max-future-minutes=5"})
class MeasurementControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MeasurementPublisher publisher;

    @MockBean
    private Clock clock;

    @org.junit.jupiter.api.BeforeEach
    void freezeClock() {
        org.mockito.Mockito.when(clock.instant()).thenReturn(NOW);
    }

    private ResultActions send(String json) throws Exception {
        return mvc.perform(post("/api/measurements").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void validMeasurementIsAcceptedAndPublished() throws Exception {
        send("""
                {"sensorId":"SENSOR-001","timestamp":"2026-11-06T09:59:00Z","powerKw":3.72,"voltage":226.4,"temperature":29.1}
                """).andExpect(status().isAccepted());
        verify(publisher).publish(any());
    }

    @Test
    void missingSensorIdIsRejectedWithReadableMessage() throws Exception {
        send("""
                {"timestamp":"2026-11-06T09:59:00Z","powerKw":3.72,"voltage":226.4}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("sensorId est obligatoire")));
        verify(publisher, never()).publish(any());
    }

    @Test
    void negativePowerIsRejected() throws Exception {
        send("""
                {"sensorId":"SENSOR-001","timestamp":"2026-11-06T09:59:00Z","powerKw":-1,"voltage":226.4}
                """).andExpect(status().isBadRequest());
        verify(publisher, never()).publish(any());
    }

    @Test
    void malformedTimestampIsRejected() throws Exception {
        send("""
                {"sensorId":"SENSOR-001","timestamp":"hier","powerKw":1,"voltage":226.4}
                """).andExpect(status().isBadRequest());
        verify(publisher, never()).publish(any());
    }

    @Test
    void tooOldMeasurementIsRejected() throws Exception {
        send("""
                {"sensorId":"SENSOR-001","timestamp":"2026-10-01T00:00:00Z","powerKw":1,"voltage":226.4}
                """).andExpect(status().isBadRequest());
        verify(publisher, never()).publish(any());
    }

    @Test
    void futureMeasurementIsRejected() throws Exception {
        send("""
                {"sensorId":"SENSOR-001","timestamp":"2026-11-06T11:00:00Z","powerKw":1,"voltage":226.4}
                """).andExpect(status().isBadRequest());
        verify(publisher, never()).publish(any());
    }
}
