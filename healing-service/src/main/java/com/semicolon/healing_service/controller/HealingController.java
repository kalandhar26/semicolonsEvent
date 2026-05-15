package com.semicolon.healing_service.controller;

import com.semicolon.healing_service.config.CircuitStateTracker;
import com.semicolon.healing_service.dto.HealingDtos.*;
import com.semicolon.healing_service.repository.CircuitEventRepository;
import com.semicolon.healing_service.repository.HealingActionRepository;
import com.semicolon.healing_service.dto.HealingDtos.CircuitActionResponse;
import com.semicolon.healing_service.service.HealingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST endpoints for manual circuit breaker control and dashboard queries.
 *
 * POST /circuit/open/{service}    — force open
 * POST /circuit/close/{service}   — force close
 * GET  /circuit/status/{service}  — current state + stats
 * GET  /circuit/history           — last 50 events (all services)
 * GET  /healing/history           — last 50 healing actions
 * GET  /status                    — health summary for all services
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HealingController {

    private final HealingService          healingService;
    private final CircuitStateTracker     stateTracker;
    private final CircuitEventRepository  circuitEventRepo;
    private final HealingActionRepository healingActionRepo;

    // ── Manual circuit control ────────────────────────────────────

    @PostMapping("/circuit/open/{service}")
    public ResponseEntity<CircuitActionResponse> openCircuit(
            @PathVariable String service) {
        log.info("Manual OPEN request for service={}", service);
        return ResponseEntity.ok(healingService.manualOpen(service));
    }

    @PostMapping("/circuit/close/{service}")
    public ResponseEntity<CircuitActionResponse> closeCircuit(
            @PathVariable String service) {
        log.info("Manual CLOSE request for service={}", service);
        return ResponseEntity.ok(healingService.manualClose(service));
    }

    // ── Status ────────────────────────────────────────────────────

    @GetMapping("/circuit/status/{service}")
    public ResponseEntity<ServiceStatusResponse> getStatus(
            @PathVariable String service) {
        return ResponseEntity.ok(ServiceStatusResponse.builder()
                .serviceName(service)
                .circuitState(stateTracker.getState(service).name())
                .consecutiveHealthyPolls(stateTracker.getHealthyPolls(service))
                .cooldownRemainingSeconds(stateTracker.cooldownRemainingSeconds(service))
                .totalHealingActions(healingActionRepo.countByServiceName(service))
                .totalCircuitEvents(circuitEventRepo.countByServiceName(service))
                .lastActionAt(stateTracker.getLastActionAt(service))
                .build());
    }

    @GetMapping("/status")
    public ResponseEntity<List<ServiceStatusResponse>> getAllStatus() {
        // Return status for all known services
        List<String> services = List.of("order-service");
        List<ServiceStatusResponse> statuses = services.stream()
                .map(service -> ServiceStatusResponse.builder()
                        .serviceName(service)
                        .circuitState(stateTracker.getState(service).name())
                        .consecutiveHealthyPolls(stateTracker.getHealthyPolls(service))
                        .cooldownRemainingSeconds(stateTracker.cooldownRemainingSeconds(service))
                        .totalHealingActions(healingActionRepo.countByServiceName(service))
                        .totalCircuitEvents(circuitEventRepo.countByServiceName(service))
                        .lastActionAt(stateTracker.getLastActionAt(service))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(statuses);
    }

    // ── History ───────────────────────────────────────────────────

    @GetMapping("/circuit/history")
    public ResponseEntity<List<CircuitEventResponse>> getCircuitHistory(
            @RequestParam(defaultValue = "50") int limit) {
        List<CircuitEventResponse> history = circuitEventRepo
                .findTop50ByOrderByTriggeredAtDesc()
                .stream()
                .limit(limit)
                .map(CircuitEventResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/circuit/history/{service}")
    public ResponseEntity<List<CircuitEventResponse>> getCircuitHistoryByService(
            @PathVariable String service) {
        List<CircuitEventResponse> history = circuitEventRepo
                .findTop20ByServiceNameOrderByTriggeredAtDesc(service)
                .stream()
                .map(CircuitEventResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/healing/history")
    public ResponseEntity<List<HealingActionResponse>> getHealingHistory(
            @RequestParam(defaultValue = "50") int limit) {
        List<HealingActionResponse> history = healingActionRepo
                .findTop50ByOrderByExecutedAtDesc()
                .stream()
                .limit(limit)
                .map(HealingActionResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    // ── Actuator health supplement ────────────────────────────────

    @GetMapping("/actuator/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(java.util.Map.of(
                "status",    "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}
