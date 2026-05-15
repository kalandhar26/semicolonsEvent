TIME    EVENT                                    WHO DOES IT
────    ─────                                    ───────────
00:00   System running normally                  Order Service
• Error rate: 2%                          processes orders
• Latency: 120ms                          normally
• AI Score: 0.05 (Normal)

00:10   JUDGE TRIGGERS FAILURE                   curl -X POST
┌──────────────────────────────────────┐  /demo/fail
│ DemoState.failing = true              │
└──────────────────────────────────────┘

00:15   FAILURES START                           Order Service
• 70% requests return HTTP 500           returns errors
• Error rate spikes to 55%               
• Latency spikes to 2000ms

00:20   PROMETHEUS SCRAPES METRICS               Prometheus
• Collects error count, latency          stores metrics
• Updates time-series database

00:25   AI PREDICTOR POLLS METRICS               AI Predictor
┌──────────────────────────────────────┐  fetches from
│ fetch_metrics_for_service()           │  Prometheus
│ • error_rate: 55%     (was 2%)       │
│ • latency_p95: 2000ms  (was 120ms)   │
│ • latency_p50: 1500ms  (was 80ms)    │
│ • req_rate: 12/s       (was 10/s)    │
│ • jvm_heap: 450MB      (was 200MB)   │
│ • gc_pause: 0.8/s      (was 0.1/s)   │
└──────────────────────────────────────┘

00:26   AI COMPUTES ANOMALY SCORE                Isolation Forest
┌──────────────────────────────────────┐  processes
│ model.predict(features)               │  6 features
│ → Runs through 100 trees              │
│ → Data point is OUTSIDE normal        │
│ → Anomaly Score: 0.87 (CRITICAL!)    │
│ → Threshold: 0.75 → IS ANOMALY!      │
└──────────────────────────────────────┘

00:26   AI SENDS ALERT                           AI Predictor
┌──────────────────────────────────────┐  → Healing Service
│ POST /anomaly/alert                   │
│ {                                     │
│   "service": "order-service",         │
│   "anomaly_score": 0.87,              │
│   "error_rate": 55.0,                 │
│   "latency_p95": 2000                 │
│ }                                     │
└──────────────────────────────────────┘

00:27   HEALING SERVICE RECEIVES ALERT           Healing Service
┌──────────────────────────────────────┐
│ handleAnomalyAlert()                  │
│                                       │
│ 1. Check: Is circuit already open?   │
│    → No, currently CLOSED             │
│                                       │
│ 2. Check: Is in cooldown?             │
│    → No, ready to act                 │
│                                       │
│ 3. ACTION: OPEN CIRCUIT!             │
│    openCircuit("order-service")       │
└──────────────────────────────────────┘

00:27   CIRCUIT BREAKER OPENS                    Healing Service
┌──────────────────────────────────────┐
│ Actions taken:                        │
│                                       │
│ ✓ State: CLOSED → OPEN               │
│ ✓ Save to PostgreSQL (circuit_events) │
│ ✓ Send Kafka command to Order Service │
│ ✓ Save healing action (healing_actions)│
│ ✓ Push WebSocket to Dashboard         │
│ ✓ Increment metrics counter           │
└──────────────────────────────────────┘

00:28   ORDER SERVICE PROTECTED                  Order Service
┌──────────────────────────────────────┐
│ Circuit breaker is now OPEN           │
│                                       │
│ • All new requests → FALLBACK         │
│ • Returns graceful response:          │
│   "Service temporarily unavailable"   │
│ • No cascading failures!              │
└──────────────────────────────────────┘

00:28   DASHBOARD UPDATES                        Dashboard
┌──────────────────────────────────────┐
│ Via WebSocket:                        │
│ • Circuit indicator: GREEN → RED     │
│ • Event log: "Circuit OPENED"         │
│ • Anomaly score: 87/100 (CRITICAL)    │
│ • Recommendation shown                │
└──────────────────────────────────────┘

        ═══════ SYSTEM IS PROTECTED ═══════

00:45   JUDGE TRIGGERS HEAL                      curl -X POST
┌──────────────────────────────────────┐  /demo/heal
│ DemoState.failing = false             │
└──────────────────────────────────────┘

00:50   METRICS START RECOVERING                 Order Service
• Error rate drops to 2%                 returns to normal
• Latency drops to 120ms

01:00   AI DETECTS RECOVERY                      AI Predictor
• Anomaly Score: 0.25 (Normal)
• Score < 0.35 (recovery threshold)

01:10   COOLDOWN EXPIRES                         Healing Service
┌──────────────────────────────────────┐
│ 30 seconds since circuit opened       │
│ → Transition to HALF_OPEN             │
└──────────────────────────────────────┘

01:10   CIRCUIT GOES HALF_OPEN                   Healing Service
┌──────────────────────────────────────┐
│ State: OPEN → HALF_OPEN               │
│ • Allows 3 test requests through      │
│ • Monitors if they succeed            │
└──────────────────────────────────────┘

01:15   PROBE 1: SUCCESS                         Healing Service
┌──────────────────────────────────────┐
│ Anomaly score still 0.25              │
│ → Healthy poll 1/3                    │
└──────────────────────────────────────┘

01:20   PROBE 2: SUCCESS                         
→ Healthy poll 2/3

01:25   PROBE 3: SUCCESS                         
→ Healthy poll 3/3

01:25   CIRCUIT CLOSES                           Healing Service
┌──────────────────────────────────────┐
│ State: HALF_OPEN → CLOSED             │
│                                       │
│ ✓ Save to PostgreSQL                  │
│ ✓ Send Kafka command                  │
│ ✓ Push WebSocket update               │
│ ✓ Dashboard: RED → GREEN             │
└──────────────────────────────────────┘

01:26   SYSTEM FULLY RECOVERED
• Order Service: Normal operation
• Circuit: CLOSED (Green)
• AI Score: 0.05 (Normal)
• All traffic flowing normally

        ═══════ SYSTEM SELF-HEALED ═══════


