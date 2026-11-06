package com.smartenergy.analytics;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "SmartEnergy Hub - API de consultation", version = "1.0",
        description = "Capteurs, historique des mesures, indicateurs, anomalies et prévision."))
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
