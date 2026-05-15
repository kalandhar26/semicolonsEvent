# ═══════════════════════════════════════════════════════════════════
#  AI Failure Prediction Platform — Makefile shortcuts
#  Usage: make <target>
# ═══════════════════════════════════════════════════════════════════

.PHONY: up down logs infra services demo-fail demo-heal status clean

## Start everything
up:
	docker-compose up --build -d
	@echo ""
	@echo "  ✓  All services starting..."
	@echo "  →  Dashboard   : http://localhost:3000"
	@echo "  →  Grafana      : http://localhost:3001  (admin / admin123)"
	@echo "  →  Prometheus   : http://localhost:9090"
	@echo "  →  AI Predictor : http://localhost:8000/docs"
	@echo "  →  Order Svc    : http://localhost:8081/actuator/health"
	@echo "  →  Healing Svc  : http://localhost:8082/actuator/health"
	@echo ""

## Start only infra (DB, Redis, Kafka, Prometheus, Grafana)
infra:
	docker-compose up -d postgres redis zookeeper kafka prometheus grafana

## Start only application services (after infra is healthy)
services:
	docker-compose up --build -d order-service healing-service ai-predictor dashboard

## Stop everything
down:
	docker-compose down

## Tail all logs
logs:
	docker-compose logs -f --tail=50

## Tail a specific service: make log s=ai-predictor
log:
	docker-compose logs -f --tail=100 $(s)

## ── DEMO COMMANDS ──────────────────────────────────────────────────

## Trigger failure on the Order Service (starts returning 500s)
demo-fail:
	curl -X POST http://localhost:8081/demo/fail
	@echo "  ✕  Order Service is now FAILING — watch the dashboard!"

## Heal the Order Service
demo-heal:
	curl -X POST http://localhost:8081/demo/heal
	@echo "  ✓  Order Service healed — circuit should close shortly."

## Get current anomaly score from AI Predictor
demo-score:
	curl -s http://localhost:8000/predict | python3 -m json.tool

## Manually open circuit breaker
demo-open:
	curl -X POST http://localhost:8082/circuit/open/order-service

## Manually close circuit breaker
demo-close:
	curl -X POST http://localhost:8082/circuit/close/order-service

## ── STATUS ─────────────────────────────────────────────────────────

## Show running containers and their health
status:
	docker-compose ps

## Pull Llama 3 into Ollama (run once, takes a few minutes)
ollama-pull:
	docker exec aiplatform-ollama ollama pull llama3
	@echo "  ✓  Llama 3 ready at http://localhost:11434"

## ── CLEANUP ────────────────────────────────────────────────────────

## Stop and delete everything including volumes (DESTRUCTIVE)
clean:
	docker-compose down -v --remove-orphans
	@echo "  ✓  All containers and volumes removed."
