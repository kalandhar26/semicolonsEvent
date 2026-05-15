package com.semicolon.order_service.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the runtime demo failure flag.
 * Toggled via POST /demo/fail and POST /demo/heal.
 * AtomicBoolean keeps it thread-safe without synchronization overhead.
 */
@Slf4j
@Component
public class DemoState {

    private final AtomicBoolean failing = new AtomicBoolean(false);

    @Getter @Setter
    private int failureLatencyMs = 800;

    public boolean isFailingMode() {
        return failing.get();
    }

    public void enableFailureMode() {
        failing.set(true);
        log.warn(">>> DEMO: Failure mode ENABLED — Order Service will return HTTP 500 <<<");
    }

    public void disableFailureMode() {
        failing.set(false);
        log.info(">>> DEMO: Failure mode DISABLED — Order Service back to normal <<<");
    }
}
