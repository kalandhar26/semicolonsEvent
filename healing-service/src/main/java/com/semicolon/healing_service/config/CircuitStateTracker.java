package com.semicolon.healing_service.config;

import com.semicolon.healing_service.model.CircuitEvent.CircuitState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks current circuit state per service in-memory.
 * Acts as the single source of truth for the Healing Service.
 * State changes are also persisted to Postgres via CircuitEventRepository.
 */
@Slf4j
@Component
public class CircuitStateTracker {

    @Value("${app.healing.cooldown-seconds:30}")
    private int cooldownSeconds;

    @Value("${app.healing.healthy-polls-required:3}")
    private int healthyPollsRequired;

    // Per-service state
    private final Map<String, CircuitState>    states         = new ConcurrentHashMap<>();
    private final Map<String, Instant>         lastActionAt   = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger>   healthyPolls   = new ConcurrentHashMap<>();

    // ── State read ────────────────────────────────────────────────
    public CircuitState getState(String service) {
        return states.getOrDefault(service, CircuitState.CLOSED);
    }

    public boolean isOpen(String service) {
        return getState(service) == CircuitState.OPEN;
    }

    public boolean isHalfOpen(String service) {
        return getState(service) == CircuitState.HALF_OPEN;
    }

    public boolean isClosed(String service) {
        return getState(service) == CircuitState.CLOSED;
    }

    // ── State transitions ─────────────────────────────────────────
    public CircuitState transitionTo(String service, CircuitState newState) {
        CircuitState previous = states.getOrDefault(service, CircuitState.CLOSED);
        states.put(service, newState);
        lastActionAt.put(service, Instant.now());
        healthyPolls.computeIfAbsent(service, k -> new AtomicInteger(0)).set(0);
        log.info("[{}] Circuit {} → {}", service, previous, newState);
        return previous;
    }

    // ── Cooldown check ────────────────────────────────────────────
    public boolean isCoolingDown(String service) {
        Instant last = lastActionAt.get(service);
        if (last == null) return false;
        long elapsed = Instant.now().getEpochSecond() - last.getEpochSecond();
        return elapsed < cooldownSeconds;
    }

    public int cooldownRemainingSeconds(String service) {
        Instant last = lastActionAt.get(service);
        if (last == null) return 0;
        long elapsed = Instant.now().getEpochSecond() - last.getEpochSecond();
        return (int) Math.max(0, cooldownSeconds - elapsed);
    }

    // ── Healthy poll counter (for auto-close from HALF_OPEN) ──────
    public int incrementHealthyPolls(String service) {
        return healthyPolls
                .computeIfAbsent(service, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public void resetHealthyPolls(String service) {
        healthyPolls.computeIfAbsent(service, k -> new AtomicInteger(0)).set(0);
    }

    public int getHealthyPolls(String service) {
        AtomicInteger counter = healthyPolls.get(service);
        return counter == null ? 0 : counter.get();
    }

    public int getHealthyPollsRequired() {
        return healthyPollsRequired;
    }

    public Instant getLastActionAt(String service) {
        return lastActionAt.get(service);
    }
}