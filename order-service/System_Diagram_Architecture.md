┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│                    http://localhost:3000                                │
│                   ┌─────────────────────┐                               │
│                   │   REACT DASHBOARD   │  Real-time visualization      │
│                   │  • Anomaly Score    │  • Green/Yellow/Red status    │
│                   │  • Circuit State    │  • Event log                  │
│                   │  • Error Rate       │  • Auto-refreshes every 5s    │
│                   └──────────┬──────────┘                               │
│                              │ WebSocket                                │
└──────────────────────────────┼──────────────────────────────────────────┘
│
┌──────────────────────────────┼──────────────────────────────────────────┐
│                    HEALING SERVICE (Port 8082)                          │
│                                                                         │
│   "The Doctor" - Decides when to protect and when to heal               │
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────┐       │
│   │  Circuit Breaker State Machine:                              │      │
│   │                                                               │     │
│   │    CLOSED ────anomaly detected───→ OPEN                      │      │
│   │      ▲                               │                        │     │
│   │      │      ┌─────────────────────┐ │                        │      │
│   │      │      │  Cooldown: 30 sec   │ │                        │      │
│   │      │      └─────────────────────┘ │                        │      │
│   │      │                               ↓                        │     │
│   │      └────3 healthy polls──── HALF_OPEN                       │     │
│   │                                      │                        │     │
│   │                          ┌───────────┴───────────┐           │      │
│   │                          │  Probe: Test requests  │           │     │
│   │                          │  If healthy → CLOSED   │           │     │
│   │                          │  If failing → OPEN     │           │     │
│   │                          └───────────────────────┘           │      │
│   └─────────────────────────────────────────────────────────────┘       │
│                                                                         │
│   Actions:                                                              │
│   • Receives alerts from AI Predictor                                   │
│   • Opens/closes circuit breaker                                        │
│   • Sends commands to Order Service via Kafka                           │
│   • Pushes real-time updates to Dashboard via WebSocket                 │
│   • Logs all actions to PostgreSQL                                      │
└─────────────────────────────────────────────────────────────────────────┘
▲                │
Alert    │                │ Circuit Command
│                ▼
┌──────────────────────────────┼──────────────────────────────────────────┐
│                    AI PREDICTOR (Port 8000)                              │
│                                                                          │
│   "The Brain" - Machine Learning that detects problems                  │
│                                                                          │
│   ┌─────────────────────────────────────────────────────────────┐      │
│   │  Isolation Forest Algorithm:                                  │      │
│   │                                                               │      │
│   │  Step 1: COLLECT METRICS (from Prometheus)                   │      │
│   │  ┌─────────────────────────────────────────────────────┐    │      │
│   │  │  • Error Rate %       (Is service returning 500s?)   │    │      │
│   │  │  • Latency p95        (How slow are responses?)      │    │      │
│   │  │  • Latency p50        (Is baseline degrading?)       │    │      │
│   │  │  • Request Rate       (Traffic volume normal?)       │    │      │
│   │  │  • JVM Heap Usage     (Memory pressure?)             │    │      │
│   │  │  • GC Pause Rate      (Garbage collection issues?)   │    │      │
│   │  └─────────────────────────────────────────────────────┘    │      │
│   │                           ↓                                  │      │
│   │  Step 2: BUILD BASELINE (Warm-up: 20 samples)              │      │
│   │  ┌─────────────────────────────────────────────────────┐    │      │
│   │  │  Creates 100 decision trees that map "normal"        │    │      │
│   │  │  behavior pattern in 6-dimensional space             │    │      │
│   │  └─────────────────────────────────────────────────────┘    │      │
│   │                           ↓                                  │      │
│   │  Step 3: DETECT ANOMALIES (Every 10 seconds)               │      │
│   │  ┌─────────────────────────────────────────────────────┐    │      │
│   │  │  New data point → Run through 100 trees              │    │      │
│   │  │                                                      │    │      │
│   │  │  SCORE:                                              │    │      │
│   │  │  0.0 - 0.45 = NORMAL   (🟢 Green)                   │    │      │
│   │  │  0.45 - 0.75 = WARNING (🟡 Yellow)                  │    │      │
│   │  │  0.75 - 1.0  = CRITICAL (🔴 Red → ALERT!)           │    │      │
│   │  └─────────────────────────────────────────────────────┘    │      │
│   │                           ↓                                  │      │
│   │  Step 4: ACT (When score > 0.75)                          │      │
│   │  ┌─────────────────────────────────────────────────────┐    │      │
│   │  │  → Sends POST /anomaly/alert to Healing Service      │    │      │
│   │  │  → Logs event in history                             │    │      │
│   │  │  → Updates Prometheus metrics                        │    │      │
│   │  │  → Retrains model every 100 samples                  │    │      │
│   │  └─────────────────────────────────────────────────────┘    │      │
│   └─────────────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────────────────┘
│
│ Pulls metrics
▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    PROMETHEUS (Port 9090)                                 │
│                                                                          │
│   "The Memory" - Time-series database for all metrics                   │
│   • Scrapes /actuator/prometheus from Order Service every 5 seconds     │
│   • Stores error rates, latency, JVM metrics, etc.                      │
│   • AI Predictor queries Prometheus for training data                   │
└──────────────────────────────────────────────────────────────────────────┘
▲
│ Exposes metrics
│
┌──────────────────────────────┼──────────────────────────────────────────┐
│                    ORDER SERVICE (Port 8081)                             │
│                                                                          │
│   "The Patient" - The microservice being monitored and protected        │
│                                                                          │
│   Normal Mode:                      Failure Mode (demo):                 │
│   ┌─────────────────────────┐       ┌─────────────────────────────┐    │
│   │ • Processes orders      │       │ • 70% of requests return    │    │
│   │ • 3-5 orders/5 seconds  │       │   HTTP 500 errors           │    │
│   │ • Response: 80-150ms    │       │ • Response: 2-5 seconds     │    │
│   │ • Error rate: <2%       │       │ • Error rate: 55-75%        │    │
│   │ • AI Score: 0.05        │       │ • AI Score: 0.85+           │    │
│   └─────────────────────────┘       └─────────────────────────────┘    │
│                                                                          │
│   Endpoints:                                                             │
│   • POST /orders        - Create order (circuit breaker protected)      │
│   • GET  /orders         - List all orders                              │
│   • POST /demo/fail      - INJECT FAILURES (for demo)                   │
│   • POST /demo/heal      - STOP FAILURES (for demo)                     │
│   • GET  /circuit/status  - Check circuit breaker state                 │
│                                                                          │
│   Protected by Resilience4j Circuit Breaker:                            │
│   • Opens when 50% of 10 calls fail                                     │
│   • Stays open 15 seconds                                               │
│   • Auto-transitions to HALF_OPEN for testing                           │
└──────────────────────────────────────────────────────────────────────────┘
▲
│ Stores/Loads
▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    DATABASES                                             │
│                                                                          │
│   PostgreSQL (5432):                  Redis (6379):                      │
│   ┌─────────────────────┐            ┌─────────────────────┐           │
│   │ • anomaly_events    │            │ • Circuit state     │           │
│   │ • circuit_events    │            │ • Latest orders     │           │
│   │ • healing_actions   │            │ • Quick lookups     │           │
│   │ • orders            │            │                     │           │
│   └─────────────────────┘            └─────────────────────┘           │
└──────────────────────────────────────────────────────────────────────────┘