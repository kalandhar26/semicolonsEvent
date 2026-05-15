package com.semicolon.order_service.controller;

import com.semicolon.order_service.config.DemoState;
import com.semicolon.order_service.dto.OrderDtos;
import com.semicolon.order_service.repository.OrderRepository;
import com.semicolon.order_service.service.OrderService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")   // Allow React dashboard to call directly
public class OrderController {

    private final OrderService          orderService;
    private final DemoState             demoState;
    private final OrderRepository       orderRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    // ══════════════════════════════════════════════════════════════
    //  ORDER CRUD
    // ══════════════════════════════════════════════════════════════

    /**
     * POST /orders
     * Creates a new order. Will return 500 when failure mode is active.
     */
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@Valid @RequestBody OrderDtos.CreateOrderRequest req) {
        try {
            OrderDtos.OrderResponse response = orderService.createOrder(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception ex) {
            // In failure mode this bubbles up as a real 500 for Prometheus to count
            log.error("Order creation failed: {}", ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",   "Order processing failed",
                            "detail",  ex.getMessage(),
                            "failing", demoState.isFailingMode()
                    ));
        }
    }

    /** GET /orders */
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDtos.OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    /** GET /orders/{id} */
    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderDtos.OrderResponse> getOrder(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.getOrder(id));
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /** GET /orders/customer/{customerId} */
    @GetMapping("/orders/customer/{customerId}")
    public ResponseEntity<List<OrderDtos.OrderResponse>> getByCustomer(
            @PathVariable String customerId) {
        return ResponseEntity.ok(orderService.getOrdersByCustomer(customerId));
    }

    // ══════════════════════════════════════════════════════════════
    //  DEMO ENDPOINTS  ← judges press these during the presentation
    // ══════════════════════════════════════════════════════════════

    /**
     * POST /demo/fail
     * Puts the Order Service into failure mode.
     * Every subsequent POST /orders returns HTTP 500.
     * The AI Predictor will detect the spike and trigger the circuit breaker.
     */
    @PostMapping("/demo/fail")
    public ResponseEntity<OrderDtos.DemoStatusResponse> triggerFailure() {
        demoState.enableFailureMode();
        log.warn("=== DEMO: Failure mode activated by judge ===");
        return ResponseEntity.ok(buildDemoStatus("Failure mode ON — POST /orders now returns 500"));
    }

    /**
     * POST /demo/heal
     * Clears the failure flag so orders succeed again.
     * The Healing Service will detect recovery and close the circuit.
     */
    @PostMapping("/demo/heal")
    public ResponseEntity<OrderDtos.DemoStatusResponse> heal() {
        demoState.disableFailureMode();
        log.info("=== DEMO: Failure mode cleared by judge ===");
        return ResponseEntity.ok(buildDemoStatus("Failure mode OFF — service recovering"));
    }

    /**
     * GET /demo/status
     * Shows current demo state — called by the React dashboard's poll loop.
     */
    @GetMapping("/demo/status")
    public ResponseEntity<OrderDtos.DemoStatusResponse> demoStatus() {
        return ResponseEntity.ok(buildDemoStatus("OK"));
    }

    // ══════════════════════════════════════════════════════════════
    //  CIRCUIT BREAKER STATUS
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /circuit/status
     * Returns Resilience4j circuit breaker state + metrics.
     * The dashboard polls this for the traffic-light indicator.
     */
    @GetMapping("/circuit/status")
    public ResponseEntity<Map<String, Object>> circuitStatus() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("orderProcessing");
        CircuitBreaker.Metrics m = cb.getMetrics();

        return ResponseEntity.ok(Map.of(
                "state",                    cb.getState().name(),
                "failureRate",              Math.round(m.getFailureRate() * 10.0) / 10.0,
                "slowCallRate",             Math.round(m.getSlowCallRate() * 10.0) / 10.0,
                "numberOfBufferedCalls",    m.getNumberOfBufferedCalls(),
                "numberOfFailedCalls",      m.getNumberOfFailedCalls(),
                "numberOfSuccessfulCalls",  m.getNumberOfSuccessfulCalls(),
                "redisState",               orderService.getCircuitStateFromRedis()
        ));
    }

    /**
     * POST /circuit/reset
     * Force-resets the Resilience4j circuit breaker to CLOSED.
     * Useful during demo recovery.
     */
    @PostMapping("/circuit/reset")
    public ResponseEntity<Map<String, String>> resetCircuit() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("orderProcessing");
        cb.reset();
        log.info("Circuit breaker manually reset to CLOSED");
        return ResponseEntity.ok(Map.of(
                "state",   cb.getState().name(),
                "message", "Circuit breaker reset to CLOSED"
        ));
    }

    // ══════════════════════════════════════════════════════════════
    //  LOAD GENERATOR (calls itself for demo realism)
    // ══════════════════════════════════════════════════════════════

    /**
     * POST /demo/load
     * Fires N synthetic orders so Prometheus has real traffic to scrape.
     * Call this once at the start of the demo to warm up metrics.
     */
    @PostMapping("/demo/load")
    public ResponseEntity<Map<String, Object>> generateLoad(
            @RequestParam(defaultValue = "20") int count) {

        int success = 0, failed = 0;
        for (int i = 0; i < count; i++) {
            try {
                orderService.createOrder(new OrderDtos.CreateOrderRequest(
                        "demo-customer-" + (i % 5),
                        "prod-" + (i % 10),
                        1,
                        java.math.BigDecimal.valueOf(29.99 + i)
                ));
                success++;
            } catch (Exception e) {
                failed++;
            }
        }
        log.info("Load gen complete: {} success, {} failed", success, failed);
        return ResponseEntity.ok(Map.of(
                "requested", count,
                "success",   success,
                "failed",    failed
        ));
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private OrderDtos.DemoStatusResponse buildDemoStatus(String message) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("orderProcessing");
        return OrderDtos.DemoStatusResponse.builder()
                .failureMode(demoState.isFailingMode())
                .circuitBreakerState(cb.getState().name())
                .totalOrders(orderRepository.count())
                .message(message)
                .build();
    }
}