package com.semicolon.healing_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "healing_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String serviceName;

    // CIRCUIT_OPEN | CIRCUIT_HALF_OPEN | CIRCUIT_CLOSE | ALERT_SENT
    @Column(nullable = false)
    private String actionType;

    // PENDING | SUCCESS | FAILED | SKIPPED (cooldown)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ActionStatus status = ActionStatus.PENDING;

    private Double anomalyScore;

    @Column(length = 512)
    private String details;

    @Column(nullable = false)
    @Builder.Default
    private Instant executedAt = Instant.now();

    private Instant completedAt;

    public enum ActionStatus {
        PENDING, SUCCESS, FAILED, SKIPPED
    }
}