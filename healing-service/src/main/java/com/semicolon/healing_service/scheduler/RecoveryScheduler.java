package com.semicolon.healing_service.scheduler;

// RecoveryScheduler.java
import com.semicolon.healing_service.config.CircuitStateTracker;
import com.semicolon.healing_service.service.HealingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Periodically polls the AI Predictor's /predict endpoint.
 * If a circuit is OPEN or HALF_OPEN, passes the latest anomaly
 * score to HealingService.checkRecovery() for auto-close logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryScheduler {

    private final HealingService      healingService;
    private final CircuitStateTracker stateTracker;

    @Qualifier("aiPredictorClient")
    private final WebClient aiPredictorClient;

    // Services we actively monitor — extend this list as you add services
    private static final List<String> MONITORED = List.of("order-service");

    @Scheduled(fixedDelay = 10_000)
    public void checkRecovery() {
        for (String service : MONITORED) {
            // Only bother polling if circuit is not already CLOSED
            if (stateTracker.isClosed(service)) continue;

            try {
                // GET /predict returns a JSON array — grab the first match
                List<Map> results = aiPredictorClient.get()
                        .uri("/predict")
                        .retrieve()
                        .bodyToFlux(Map.class)
                        .collectList()
                        .block();

                if (results == null || results.isEmpty()) continue;

                double score = results.stream()
                        .filter(r -> service.equals(r.get("service")))
                        .mapToDouble(r -> ((Number) r.getOrDefault("anomaly_score", 0.0)).doubleValue())
                        .findFirst()
                        .orElse(0.0);

                log.debug("[{}] Recovery poll — anomaly score={}", service, score);
                healingService.checkRecovery(service, score);

            } catch (Exception ex) {
                log.warn("[{}] Recovery poll failed — AI Predictor unreachable: {}", service, ex.getMessage());
            }
        }
    }
}