# AI Failure Prediction Platform — Quickstart

## Directory structure

Place the files like this before running `docker-compose up`:

```
project-root/
├── docker-compose.yml
├── Makefile
│
├── infra/
│   ├── postgres/
│   │   └── init.sql                  ← DB schema
│   ├── prometheus/
│   │   └── prometheus.yml            ← scrape config
│   └── grafana/
│       └── provisioning/             ← auto-provision datasource
│
├── order-service/                    ← Spring Boot
│   ├── Dockerfile                    ← copy Dockerfile.springboot here
│   ├── pom.xml
│   └── src/
│
├── healing-service/                  ← Spring Boot
│   ├── Dockerfile                    ← copy Dockerfile.springboot here
│   ├── pom.xml
│   └── src/
│
├── ai-predictor/                     ← Python FastAPI
│   ├── Dockerfile                    ← copy Dockerfile.ai-predictor here
│   ├── requirements.txt
│   └── main.py
│
└── aipredictordashboard/                        ← React
    ├── Dockerfile                    ← copy Dockerfile.dashboard here
    ├── nginx.conf
    ├── package.json
    └── src/
        └── AIPredictorDashboard.jsx
```

## Quickstart

```bash
# 1. Start everything
make up
# or: docker-compose up --build -d

# 2. Wait ~60s for Spring Boot services to boot, then open:
#    Dashboard  → http://localhost:3000
#    Grafana    → http://localhost:3001  (admin / admin123)
#    AI API     → http://localhost:8000/docs

# 3. Demo sequence (for judges):
make demo-fail    # inject 500 errors
# wait ~15 seconds — watch anomaly score spike and circuit open
make demo-heal    # recover the service

# 4. Individual service logs
make log s=ai-predictor
make log s=order-service

# 5. Tear down
make clean
```

## Ports at a glance

| Service       | Port  | Notes                        |
|---------------|-------|------------------------------|
| Dashboard     | 3000  | React UI                     |
| Grafana       | 3001  | admin / admin123             |
| Order Service | 8081  | Spring Boot Actuator exposed |
| Healing Svc   | 8082  | Circuit breaker REST API     |
| AI Predictor  | 8000  | FastAPI + /docs (Swagger)    |
| Prometheus    | 9090  | Metrics scraper              |
| Kafka         | 9092  | External broker access       |
| PostgreSQL    | 5432  | aiplatform / aiplatform123   |
| Redis         | 6379  | redis123                     |
| Ollama        | 11434 | Optional — remove if low RAM |

## Skip Ollama (low RAM / faster startup)

Comment out or delete the `ollama` block at the bottom of docker-compose.yml.

## Expose to judges via ngrok

```bash
ngrok http 3000
```

Share the ngrok URL — all API proxying goes through nginx so no CORS issues.

| Service        | URL                                     | How to Test         |
|----------------|-----------------------------------------|---------------------|
| Dashboard      | `http://localhost:3000`                 | Open in browser     |
| AI API Docs    | `http://localhost:8000/docs`            | Interactive Swagger |
| AI Health      | `http://localhost:8000/health`          | curl command        |
| AI Predict     | `http://localhost:8000/predict`         | curl command        |
| Order Health   | `http://localhost:8081/actuator/health` | curl command        |
| Order API      | `http://localhost:8081/orders`          | curl command        |
| Healing Health | `http://localhost:8082/actuator/health` | curl command        |
| Prometheus     | `http://localhost:9090`                 | Open in browser     |
| Grafana        | `http://localhost:3001`                 | `admin / admin123`  |


1. "Our Order Service is a microservice processing orders"
2. "The AI Predictor watches 6 metrics simultaneously"
3. "When I inject failures, the AI detects it in seconds"
4. "The Healing Service automatically opens the circuit breaker"
5. "The Dashboard shows everything in real-time"
6. "When I heal the service, recovery is automatic"
7. "Zero human intervention needed - true self-healing"
8. "Ollama provides AI-powered root cause analysis"