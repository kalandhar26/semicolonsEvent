package com.semicolon.order_service.dto;

import com.semicolon.order_service.model.Order;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderDtos {

    // ── Inbound ───────────────────────────────────────────────────
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {

        @NotBlank(message = "customerId is required")
        private String customerId;

        @NotBlank(message = "productId is required")
        private String productId;

        @NotNull
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;

        @NotNull
        @DecimalMin(value = "0.01", message = "totalAmount must be positive")
        private BigDecimal totalAmount;
    }

    // ── Outbound ──────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private Long id;
        private String customerId;
        private String productId;
        private Integer quantity;
        private BigDecimal totalAmount;
        private Order.OrderStatus status;
        private Instant createdAt;
        private Instant updatedAt;

        public static OrderResponse from(Order o) {
            return OrderResponse.builder()
                    .id(o.getId())
                    .customerId(o.getCustomerId())
                    .productId(o.getProductId())
                    .quantity(o.getQuantity())
                    .totalAmount(o.getTotalAmount())
                    .status(o.getStatus())
                    .createdAt(o.getCreatedAt())
                    .updatedAt(o.getUpdatedAt())
                    .build();
        }
    }

    // ── Kafka event ───────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderEvent {
        private Long orderId;
        private String customerId;
        private String eventType;   // ORDER_CREATED, ORDER_FAILED, etc.
        private Instant occurredAt;
    }

    // ── Demo / health ─────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DemoStatusResponse {
        private boolean failureMode;
        private String  circuitBreakerState;
        private long    totalOrders;
        private String  message;
    }
}