package com.semicolon.healing_service.dto;

import com.semicolon.healing_service.model.CircuitEvent;
import com.semicolon.healing_service.model.HealingAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class HealingDtos {

    // ── Inbound from AI Predictor (POST /anomaly/alert) ───────────
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyAlertRequest {

        @NotBlank
        private String service;

        @NotNull
        private Double anomalyScore;

        private Double errorRate;

        private Double latencyP95;

        private String timestamp;
    }

    // ── Kafka message published to "circuit-events" topic ─────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitCommandEvent {
        private String service;
        private String action;       // OPEN | CLOSE | HALF_OPEN
        private String reason;
        private Double anomalyScore;
        private Instant issuedAt;
    }

    // ── WebSocket push to React dashboard ─────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardPushEvent {
        private String type;          // ANOMALY | CIRCUIT_OPEN | CIRCUIT_HALF_OPEN | CIRCUIT_CLOSE | HEALED
        private String service;
        private String circuitState;  // CLOSED | OPEN | HALF_OPEN
        private Double anomalyScore;
        private Double errorRate;
        private Double latencyP95;
        private String message;
        private Instant timestamp;
    }

    // ── REST response for manual circuit actions ───────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitActionResponse {
        private String service;
        private String previousState;
        private String newState;
        private String action;
        private String message;
        private Instant timestamp;
    }

    // ── Summary response for GET /status ──────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceStatusResponse {
        private String serviceName;
        private String circuitState;
        private Integer consecutiveHealthyPolls;
        private Integer cooldownRemainingSeconds;
        private Long totalHealingActions;
        private Long totalCircuitEvents;
        private Instant lastActionAt;
    }

    // ── Projection for history responses ──────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitEventResponse {
        private Long id;
        private String serviceName;
        private String fromState;
        private String toState;
        private Double anomalyScore;
        private Double errorRate;
        private String reason;
        private String triggerType;
        private Instant triggeredAt;

        public static CircuitEventResponse from(CircuitEvent e) {
            return CircuitEventResponse.builder()
                    .id(e.getId())
                    .serviceName(e.getServiceName())
                    .fromState(e.getFromState().name())
                    .toState(e.getToState().name())
                    .anomalyScore(e.getAnomalyScore())
                    .errorRate(e.getErrorRate())
                    .reason(e.getReason())
                    .triggerType(e.getTriggerType())
                    .triggeredAt(e.getTriggeredAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealingActionResponse {
        private Long id;
        private String serviceName;
        private String actionType;
        private String status;
        private Double anomalyScore;
        private String details;
        private Instant executedAt;
        private Instant completedAt;

        public static HealingActionResponse from(HealingAction a) {
            return HealingActionResponse.builder()
                    .id(a.getId())
                    .serviceName(a.getServiceName())
                    .actionType(a.getActionType())
                    .status(a.getStatus().name())
                    .anomalyScore(a.getAnomalyScore())
                    .details(a.getDetails())
                    .executedAt(a.getExecutedAt())
                    .completedAt(a.getCompletedAt())
                    .build();
        }
    }
}