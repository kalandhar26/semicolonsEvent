-- ═══════════════════════════════════════════════════════
--  AI Failure Prediction Platform — DB Init
-- ═══════════════════════════════════════════════════════

-- Anomaly events logged by the AI predictor
CREATE TABLE IF NOT EXISTS anomaly_events (
    id            BIGSERIAL PRIMARY KEY,
    service_name  VARCHAR(128)     NOT NULL,
    anomaly_score DOUBLE PRECISION NOT NULL,
    error_rate    DOUBLE PRECISION,
    latency_ms    DOUBLE PRECISION,
    detected_at   TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    resolved_at   TIMESTAMPTZ,
    notes         TEXT
);

-- Circuit breaker state transitions
CREATE TABLE IF NOT EXISTS circuit_events (
    id           BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(128) NOT NULL,
    from_state   VARCHAR(32)  NOT NULL,  -- CLOSED, OPEN, HALF_OPEN
    to_state     VARCHAR(32)  NOT NULL,
    reason       TEXT,
    triggered_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Healing actions taken by the healing service
CREATE TABLE IF NOT EXISTS healing_actions (
    id           BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(128) NOT NULL,
    action_type  VARCHAR(64)  NOT NULL,  -- CIRCUIT_OPEN, CIRCUIT_CLOSE, RESTART, SCALE
    status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',  -- PENDING, SUCCESS, FAILED
    anomaly_id   BIGINT       REFERENCES anomaly_events(id),
    executed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    details      JSONB
);

-- Indexes for dashboard queries
CREATE INDEX IF NOT EXISTS idx_anomaly_detected_at  ON anomaly_events (detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_anomaly_service       ON anomaly_events (service_name);
CREATE INDEX IF NOT EXISTS idx_circuit_triggered_at  ON circuit_events (triggered_at DESC);
CREATE INDEX IF NOT EXISTS idx_healing_executed_at   ON healing_actions (executed_at DESC);

-- Seed a welcome row so the dashboard shows something on first launch
INSERT INTO anomaly_events (service_name, anomaly_score, error_rate, latency_ms, notes)
VALUES ('order-service', 0.05, 1.2, 118.0, 'System initialised — baseline recorded');
