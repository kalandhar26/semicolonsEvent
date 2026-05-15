package com.semicolon.order_service.kafka;

import com.semicolon.order_service.config.DemoState;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens to the "circuit-events" Kafka topic.
 * When the Healing Service broadcasts OPEN/CLOSE commands,
 * this consumer syncs the local Resilience4j circuit breaker state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitEventConsumer {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final DemoState demoState;

    @KafkaListener(topics = "circuit-events", groupId = "order-service-circuit-group")
    public void onCircuitEvent(Map<String, Object> event) {
        String targetService = (String) event.get("service");
        String action = (String) event.get("action");   // OPEN | CLOSE | HALF_OPEN

        if (!"order-service".equals(targetService)) return;

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("orderProcessing");

        switch (action) {
            case "OPEN" -> {
                cb.transitionToOpenState();
                log.warn("[Kafka] Circuit breaker forced OPEN by Healing Service");
            }
            case "CLOSE" -> {
                cb.transitionToClosedState();
                // Also clear failure mode if it was a manual heal
                demoState.disableFailureMode();
                log.info("[Kafka] Circuit breaker forced CLOSED by Healing Service");
            }
            case "HALF_OPEN" -> {
                cb.transitionToHalfOpenState();
                log.info("[Kafka] Circuit breaker transitioned to HALF_OPEN");
            }
            default -> log.warn("[Kafka] Unknown circuit action: {}", action);
        }
    }
}
