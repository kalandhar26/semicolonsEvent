package com.semicolon.order_service.scheduler;

import com.semicolon.order_service.dto.OrderDtos;
import com.semicolon.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;

/**
 * Fires synthetic orders on a fixed schedule so Prometheus always has
 * fresh http.server.requests metrics to scrape — even when no real
 * client is sending traffic.
 *
 * During failure mode these requests all return 500, causing the
 * error_rate metric to spike and the AI Predictor to detect the anomaly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyntheticLoadScheduler {

    private final OrderService orderService;
    private final Random random = new Random();

    // Fire 3 orders every 5 seconds — gives Prometheus enough data points
    @Scheduled(fixedDelay = 5000)
    public void generateSyntheticTraffic() {
        int batch = 3 + random.nextInt(3); // 3–5 requests per tick
        for (int i = 0; i < batch; i++) {
            try {
                orderService.createOrder(new OrderDtos.CreateOrderRequest(
                        "synthetic-customer-" + random.nextInt(10),
                        "prod-" + random.nextInt(20),
                        1 + random.nextInt(5),
                        BigDecimal.valueOf(9.99 + random.nextInt(100))
                ));
            } catch (Exception ex) {
                // Swallow — failure mode exceptions are expected and already counted
                // by Micrometer via the http.server.requests auto-instrument
                log.debug("Synthetic order failed (expected in failure mode): {}", ex.getMessage());
            }
        }
    }
}