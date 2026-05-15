package com.semicolon.healing_service.controller;

import com.semicolon.healing_service.dto.HealingDtos.AnomalyAlertRequest;
import com.semicolon.healing_service.service.HealingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Receives anomaly alerts from the AI Predictor service.
 * This is the primary entry point that kicks off the healing workflow.
 *
 * Called by ai-predictor/main.py → notify_healing_service()
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnomalyAlertController {

    private final HealingService healingService;

    /**
     * POST /anomaly/alert
     * AI Predictor calls this when anomaly score exceeds threshold.
     *
     * Body example:
     * {
     *   "service":      "order-service",
     *   "anomalyScore": 0.87,
     *   "errorRate":    62.3,
     *   "latencyP95":   1240.0,
     *   "timestamp":    "2024-01-15T10:30:00Z"
     * }
     */
    @PostMapping("/anomaly/alert")
    public ResponseEntity<Map<String, Object>> receiveAlert(
            @Valid @RequestBody AnomalyAlertRequest alert) {

        log.warn("Anomaly alert received — service={} score={} errorRate={}%",
                alert.getService(), alert.getAnomalyScore(), alert.getErrorRate());

        healingService.handleAnomalyAlert(alert);

        return ResponseEntity.accepted().body(Map.of(
                "status",    "accepted",
                "service",   alert.getService(),
                "score",     alert.getAnomalyScore(),
                "timestamp", Instant.now().toString()
        ));
    }
}