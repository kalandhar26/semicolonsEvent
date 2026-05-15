package com.semicolon.healing_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persisted record of every circuit breaker state transition.
 * Written by HealingService, read by the dashboard history endpoint.
 */
@Entity
@Table(name = "circuit_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", nullable = false, length = 32)
    private CircuitState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 32)
    private CircuitState toState;

    // Anomaly score that triggered this transition (null for manual actions)
    private Double anomalyScore;

    private Double errorRate;

    private Double latencyP95;

    @Column(name = "reason", columnDefinition = "TEXT",length = 512)
    private String reason;

    // ANOMALY_DETECTED | MANUAL | AUTO_RECOVERY | PROBE_SUCCESS | PROBE_FAILURE
    @Column(nullable = false)
    private String triggerType;

    @Column(nullable = false)
    @Builder.Default
    private Instant triggeredAt = Instant.now();

    private Instant resolvedAt;

    public enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }
}
