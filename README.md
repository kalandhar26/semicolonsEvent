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
└── dashboard/                        ← React
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

| Service        | Port  | Notes                        |
|----------------|-------|------------------------------|
| Dashboard      | 3000  | React UI                     |
| Grafana        | 3001  | admin / admin123             |
| Order Service  | 8081  | Spring Boot Actuator exposed |
| Healing Svc    | 8082  | Circuit breaker REST API     |
| AI Predictor   | 8000  | FastAPI + /docs (Swagger)    |
| Prometheus     | 9090  | Metrics scraper              |
| Kafka          | 9092  | External broker access       |
| PostgreSQL     | 5432  | aiplatform / aiplatform123   |
| Redis          | 6379  | redis123                     |
| Ollama         | 11434 | Optional — remove if low RAM |

## Skip Ollama (low RAM / faster startup)

Comment out or delete the `ollama` block at the bottom of docker-compose.yml.

## Expose to judges via ngrok

```bash
ngrok http 3000
```
Share the ngrok URL — all API proxying goes through nginx so no CORS issues.
