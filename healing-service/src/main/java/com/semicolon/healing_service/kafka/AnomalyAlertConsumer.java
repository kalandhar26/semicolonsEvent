package com.semicolon.healing_service.kafka;


import com.semicolon.healing_service.dto.HealingDtos.AnomalyAlertRequest;
import com.semicolon.healing_service.service.HealingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Secondary inbound channel for anomaly alerts via Kafka.
 *
 * The AI Predictor primarily calls POST /anomaly/alert (REST).
 * This consumer handles the same event if it's published to Kafka,
 * giving you dual delivery and decoupling for free.
 *
 * Message format matches AnomalyAlertRequest fields.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyAlertConsumer {

    private final HealingService healingService;

    @KafkaListener(topics = "anomaly-alerts", groupId = "healing-service-group")
    public void onAnomalyAlert(Map<String, Object> payload) {
        try {
            String service = (String) payload.get("service");
            double score   = ((Number) payload.getOrDefault("anomalyScore", 0.0)).doubleValue();
            double errRate = ((Number) payload.getOrDefault("errorRate",    0.0)).doubleValue();
            double lat95   = ((Number) payload.getOrDefault("latencyP95",   0.0)).doubleValue();

            log.info("[Kafka] Anomaly alert — service={} score={}", service, score);

            AnomalyAlertRequest alert = new AnomalyAlertRequest(
                    service, score, errRate, lat95, null);

            healingService.handleAnomalyAlert(alert);

        } catch (Exception ex) {
            log.error("[Kafka] Failed to process anomaly alert: {}", ex.getMessage(), ex);
        }
    }
}
