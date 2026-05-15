package com.semicolon.healing_service.service;

import com.semicolon.healing_service.config.CircuitStateTracker;
import com.semicolon.healing_service.dto.HealingDtos.AnomalyAlertRequest;
import com.semicolon.healing_service.dto.HealingDtos.CircuitActionResponse;
import com.semicolon.healing_service.dto.HealingDtos.CircuitCommandEvent;
import com.semicolon.healing_service.dto.HealingDtos.DashboardPushEvent;
import com.semicolon.healing_service.model.CircuitEvent;
import com.semicolon.healing_service.model.CircuitEvent.CircuitState;
import com.semicolon.healing_service.model.HealingAction;
import com.semicolon.healing_service.repository.CircuitEventRepository;
import com.semicolon.healing_service.repository.HealingActionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealingService {

    private final CircuitStateTracker      stateTracker;
    private final CircuitEventRepository   circuitEventRepo;
    private final HealingActionRepository  healingActionRepo;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimpMessagingTemplate    wsTemplate;
    private final MeterRegistry            meterRegistry;

    @Qualifier("orderServiceClient")
    private final WebClient orderServiceClient;

    @Value("${app.healing.recovery-threshold:0.35}")
    private double recoveryThreshold;

    @Value("${app.healing.healthy-polls-required:3}")
    private int healthyPollsRequired;

    // ── Custom metrics ────────────────────────────────────────────
    private Counter circuitOpens() {
        return meterRegistry.counter("healing.circuit.opens.total");
    }
    private Counter circuitCloses() {
        return meterRegistry.counter("healing.circuit.closes.total");
    }
    private Counter healingActions() {
        return meterRegistry.counter("healing.actions.total");
    }

    // ═════════════════════════════════════════════════════════════
    //  MAIN ENTRY — called by AnomalyAlertController on POST /anomaly/alert
    // ═════════════════════════════════════════════════════════════

    @Transactional
    public void handleAnomalyAlert(AnomalyAlertRequest alert) {
        String service = alert.getService();
        double score   = alert.getAnomalyScore();

        log.warn("[{}] Anomaly alert received — score={} errorRate={}% latencyP95={}ms",
                service, score, alert.getErrorRate(), alert.getLatencyP95());

        // Already open — just push a WebSocket update, don't double-act
        if (stateTracker.isOpen(service)) {
            log.info("[{}] Circuit already OPEN — skipping re-trigger", service);
            pushWebSocketEvent(buildPushEvent(service, "ANOMALY",
                    "Circuit already OPEN — anomaly score: " + score, score, alert));
            return;
        }

        // Cooldown guard — avoid flapping
        if (stateTracker.isCoolingDown(service)) {
            int remaining = stateTracker.cooldownRemainingSeconds(service);
            log.info("[{}] In cooldown — {} seconds remaining, skipping", service, remaining);
            return;
        }

        // ── ACTION: Open the circuit ──────────────────────────────
        openCircuit(service, score, alert, "ANOMALY_DETECTED");
    }

    // ═════════════════════════════════════════════════════════════
    //  RECOVERY POLL — called by RecoveryScheduler every 10s
    // ═════════════════════════════════════════════════════════════

    @Transactional
    public void checkRecovery(String service, double latestScore) {

        if (stateTracker.isClosed(service)) return;

        if (stateTracker.isOpen(service)) {
            // Transition to HALF_OPEN after cooldown expires
            if (!stateTracker.isCoolingDown(service)) {
                log.info("[{}] Cooldown expired — transitioning to HALF_OPEN", service);
                halfOpenCircuit(service, latestScore);
            }
            return;
        }

        // ── In HALF_OPEN: probe whether service has recovered ─────
        if (stateTracker.isHalfOpen(service)) {
            if (latestScore <= recoveryThreshold) {
                int healthy = stateTracker.incrementHealthyPolls(service);
                log.info("[{}] HALF_OPEN probe OK ({}/{}) score={}",
                        service, healthy, healthyPollsRequired, latestScore);

                if (healthy >= healthyPollsRequired) {
                    closeCircuit(service, latestScore, "AUTO_RECOVERY");
                } else {
                    pushWebSocketEvent(DashboardPushEvent.builder()
                            .type("PROBE_OK")
                            .service(service)
                            .circuitState("HALF_OPEN")
                            .anomalyScore(latestScore)
                            .message("Probe " + healthy + "/" + healthyPollsRequired + " healthy")
                            .timestamp(Instant.now())
                            .build());
                }
            } else {
                // Still unhealthy — re-open
                log.warn("[{}] HALF_OPEN probe FAILED score={} — reopening circuit", service, latestScore);
                stateTracker.resetHealthyPolls(service);
                openCircuit(service, latestScore, null, "PROBE_FAILURE");
            }
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  MANUAL ACTIONS — called by HealingController
    // ═════════════════════════════════════════════════════════════

    @Transactional
    public CircuitActionResponse manualOpen(String service) {
        CircuitState prev = stateTracker.transitionTo(service, CircuitState.OPEN);
        persistCircuitEvent(service, prev, CircuitState.OPEN, null, null, "Manual open", "MANUAL");
        publishCircuitCommand(service, "OPEN", "Manual trigger via API", null);
        persistHealingAction(service, "CIRCUIT_OPEN", HealingAction.ActionStatus.SUCCESS, null,
                "Manually opened via REST API");
        pushWebSocketEvent(DashboardPushEvent.builder()
                .type("CIRCUIT_OPEN")
                .service(service)
                .circuitState("OPEN")
                .message("Circuit manually OPENED")
                .timestamp(Instant.now())
                .build());
        circuitOpens().increment();
        return CircuitActionResponse.builder()
                .service(service).previousState(prev.name()).newState("OPEN")
                .action("OPEN").message("Circuit breaker manually opened")
                .timestamp(Instant.now()).build();
    }

    @Transactional
    public CircuitActionResponse manualClose(String service) {
        CircuitState prev = stateTracker.transitionTo(service, CircuitState.CLOSED);
        persistCircuitEvent(service, prev, CircuitState.CLOSED, null, null, "Manual close", "MANUAL");
        publishCircuitCommand(service, "CLOSE", "Manual close via API", null);
        persistHealingAction(service, "CIRCUIT_CLOSE", HealingAction.ActionStatus.SUCCESS, null,
                "Manually closed via REST API");
        pushWebSocketEvent(DashboardPushEvent.builder()
                .type("CIRCUIT_CLOSE")
                .service(service)
                .circuitState("CLOSED")
                .message("Circuit manually CLOSED — traffic resuming")
                .timestamp(Instant.now())
                .build());
        circuitCloses().increment();
        return CircuitActionResponse.builder()
                .service(service).previousState(prev.name()).newState("CLOSED")
                .action("CLOSE").message("Circuit breaker manually closed")
                .timestamp(Instant.now()).build();
    }

    // ═════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════

    private void openCircuit(String service, double score,
                             AnomalyAlertRequest alert, String triggerType) {
        CircuitState prev = stateTracker.transitionTo(service, CircuitState.OPEN);

        String reason = String.format(
                "Anomaly score %.2f exceeded threshold. Error rate: %.1f%%, Latency p95: %.0fms",
                score,
                alert != null ? alert.getErrorRate() : 0.0,
                alert != null ? alert.getLatencyP95() : 0.0);

        persistCircuitEvent(service, prev, CircuitState.OPEN, score,
                alert != null ? alert.getErrorRate() : null, reason, triggerType);

        publishCircuitCommand(service, "OPEN", reason, score);

        persistHealingAction(service, "CIRCUIT_OPEN",
                HealingAction.ActionStatus.SUCCESS, score, reason);

        pushWebSocketEvent(DashboardPushEvent.builder()
                .type("CIRCUIT_OPEN")
                .service(service)
                .circuitState("OPEN")
                .anomalyScore(score)
                .errorRate(alert != null ? alert.getErrorRate() : null)
                .latencyP95(alert != null ? alert.getLatencyP95() : null)
                .message("🔴 Circuit OPENED — " + reason)
                .timestamp(Instant.now())
                .build());

        circuitOpens().increment();
        healingActions().increment();

        log.warn("[{}] Circuit OPENED — score={} reason={}", service, score, reason);
    }

    private void halfOpenCircuit(String service, double score) {
        CircuitState prev = stateTracker.transitionTo(service, CircuitState.HALF_OPEN);

        persistCircuitEvent(service, prev, CircuitState.HALF_OPEN, score, null,
                "Cooldown expired — probing service", "AUTO_RECOVERY");

        publishCircuitCommand(service, "HALF_OPEN", "Cooldown expired, probing", score);

        pushWebSocketEvent(DashboardPushEvent.builder()
                .type("CIRCUIT_HALF_OPEN")
                .service(service)
                .circuitState("HALF_OPEN")
                .anomalyScore(score)
                .message("🟡 Circuit HALF-OPEN — sending probe requests")
                .timestamp(Instant.now())
                .build());

        log.info("[{}] Circuit HALF_OPEN — probing recovery", service);
    }

    private void closeCircuit(String service, double score, String triggerType) {
        CircuitState prev = stateTracker.transitionTo(service, CircuitState.CLOSED);

        String reason = String.format(
                "Service recovered — anomaly score %.2f below threshold %.2f for %d consecutive polls",
                score, recoveryThreshold, healthyPollsRequired);

        persistCircuitEvent(service, prev, CircuitState.CLOSED, score, null, reason, triggerType);

        publishCircuitCommand(service, "CLOSE", reason, score);

        persistHealingAction(service, "CIRCUIT_CLOSE",
                HealingAction.ActionStatus.SUCCESS, score, reason);

        pushWebSocketEvent(DashboardPushEvent.builder()
                .type("CIRCUIT_CLOSE")
                .service(service)
                .circuitState("CLOSED")
                .anomalyScore(score)
                .message("✅ Circuit CLOSED — " + reason)
                .timestamp(Instant.now())
                .build());

        circuitCloses().increment();
        healingActions().increment();

        log.info("[{}] Circuit CLOSED — service healthy. score={}", service, score);
    }

    // Publish command to Kafka — Order Service consumes and syncs Resilience4j state
    private void publishCircuitCommand(String service, String action, String reason, Double score) {
        CircuitCommandEvent cmd = CircuitCommandEvent.builder()
                .service(service)
                .action(action)
                .reason(reason)
                .anomalyScore(score)
                .issuedAt(Instant.now())
                .build();
        kafkaTemplate.send("circuit-events", service, cmd);
        log.debug("[{}] Kafka circuit command published: {}", service, action);
    }

    // Push real-time update to all subscribed React dashboard clients
    private void pushWebSocketEvent(DashboardPushEvent event) {
        try {
            wsTemplate.convertAndSend("/topic/events", event);
        } catch (Exception e) {
            log.warn("WebSocket push failed: {}", e.getMessage());
        }
    }

    private void persistCircuitEvent(String service, CircuitState from, CircuitState to,
                                     Double score, Double errorRate, String reason, String trigger) {
        circuitEventRepo.save(CircuitEvent.builder()
                .serviceName(service)
                .fromState(from)
                .toState(to)
                .anomalyScore(score)
                .errorRate(errorRate)
                .reason(reason)
                .triggerType(trigger)
                .triggeredAt(Instant.now())
                .build());
    }

    private void persistHealingAction(String service, String actionType,
                                      HealingAction.ActionStatus status,
                                      Double score, String details) {
        HealingAction action = HealingAction.builder()
                .serviceName(service)
                .actionType(actionType)
                .status(status)
                .anomalyScore(score)
                .details(details)
                .executedAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        healingActionRepo.save(action);
    }

    private DashboardPushEvent buildPushEvent(String service, String type,
                                              String message, double score,
                                              AnomalyAlertRequest alert) {
        return DashboardPushEvent.builder()
                .type(type)
                .service(service)
                .circuitState(stateTracker.getState(service).name())
                .anomalyScore(score)
                .errorRate(alert != null ? alert.getErrorRate() : null)
                .latencyP95(alert != null ? alert.getLatencyP95() : null)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}