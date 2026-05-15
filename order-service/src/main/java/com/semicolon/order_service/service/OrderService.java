package com.semicolon.order_service.service;

import com.semicolon.order_service.config.DemoState;
import com.semicolon.order_service.dto.OrderDtos;
import com.semicolon.order_service.model.Order;
import com.semicolon.order_service.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final DemoState demoState;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    // Redis key for circuit breaker state (written by Healing Service too)
    private static final String CB_STATE_KEY = "circuit:order-service:state";

    // ── Custom Micrometer metrics ─────────────────────────────────────────────
    // Spring Boot auto-creates http.server.requests — we add business metrics.
    private Counter ordersCreated() {
        return meterRegistry.counter("orders.created.total");
    }

    private Counter ordersFailed() {
        return meterRegistry.counter("orders.failed.total");
    }

    private Timer orderProcessingTime() {
        return meterRegistry.timer("orders.processing.duration");
    }

    // ── Create order — wrapped in Resilience4j circuit breaker ───────────────
    @Transactional
    @CircuitBreaker(name = "orderProcessing", fallbackMethod = "createOrderFallback")
    public OrderDtos.OrderResponse createOrder(OrderDtos.CreateOrderRequest req) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // ── DEMO FAILURE INJECTION ────────────────────────────────────────
            if (demoState.isFailingMode()) {
                // Add realistic latency before failing (makes the latency chart spike too)
                simulateSlowness(demoState.getFailureLatencyMs());
                ordersFailed().increment();
                throw new RuntimeException(
                        "Simulated failure: downstream payment gateway timed out"
                );
            }

            // ── Normal processing ─────────────────────────────────────────────
            simulateSlowness(50); // realistic baseline processing time

            Order order = Order.builder()
                    .customerId(req.getCustomerId())
                    .productId(req.getProductId())
                    .quantity(req.getQuantity())
                    .totalAmount(req.getTotalAmount())
                    .status(Order.OrderStatus.CONFIRMED)
                    .build();

            Order saved = orderRepository.save(order);
            ordersCreated().increment();

            // Publish to Kafka
            publishOrderEvent(saved, "ORDER_CREATED");

            // Store latest order id in Redis for quick lookups
            redisTemplate.opsForValue().set(
                    "order:latest:" + req.getCustomerId(),
                    saved.getId(),
                    Duration.ofMinutes(10)
            );

            log.info("Order created — id={} customer={} product={}",
                    saved.getId(), saved.getCustomerId(), saved.getProductId());

            return OrderDtos.OrderResponse.from(saved);

        } finally {
            sample.stop(orderProcessingTime());
        }
    }

    // ── Fallback when circuit breaker is OPEN ────────────────────────────────
    public OrderDtos.OrderResponse createOrderFallback(
            OrderDtos.CreateOrderRequest req, Throwable ex) {

        log.warn("Circuit breaker OPEN — fallback triggered for customer={}: {}",
                req.getCustomerId(), ex.getMessage());

        // Return a graceful degraded response instead of a raw 500
        return OrderDtos.OrderResponse.builder()
                .customerId(req.getCustomerId())
                .productId(req.getProductId())
                .quantity(req.getQuantity())
                .totalAmount(req.getTotalAmount())
                .status(Order.OrderStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    // ── Read operations (no circuit breaker needed — reads are safe) ─────────
    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(OrderDtos.OrderResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse getOrder(Long id) {
        return orderRepository.findById(id)
                .map(OrderDtos.OrderResponse::from)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(OrderDtos.OrderResponse::from)
                .collect(Collectors.toList());
    }

    // ── Circuit state from Redis (set by Healing Service) ────────────────────
    public String getCircuitStateFromRedis() {
        Object state = redisTemplate.opsForValue().get(CB_STATE_KEY);
        return state != null ? state.toString() : "CLOSED";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void simulateSlowness(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void publishOrderEvent(Order order, String eventType) {
        try {
            OrderDtos.OrderEvent event = OrderDtos.OrderEvent.builder()
                    .orderId(order.getId())
                    .customerId(order.getCustomerId())
                    .eventType(eventType)
                    .occurredAt(Instant.now())
                    .build();
            kafkaTemplate.send("orders", order.getCustomerId(), event);
        } catch (Exception e) {
            // Don't fail the order if Kafka is down — log and continue
            log.warn("Failed to publish Kafka event for order {}: {}", order.getId(), e.getMessage());
        }
    }
}
