# ============================================================
# Makefile — Common development commands
# ============================================================

.PHONY: help run build test docker-up docker-down docker-build docker-logs db-reset lint

help:                              ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ── Local development ──────────────────────────────────────

run:                               ## Run the backend locally via Gradle
	cd backend && ./gradlew --no-daemon run

build:                             ## Build the backend fat JAR (skip tests)
	cd backend && ./gradlew --no-daemon clean build -x test

test:                              ## Run backend tests
	cd backend && ./gradlew --no-daemon test

lint:                              ## Compile-check Kotlin (lint)
	cd backend && ./gradlew --no-daemon compileKotlin compileTestKotlin

# ── Docker ─────────────────────────────────────────────────

docker-up:                         ## Start full stack with Docker Compose
	docker compose -f docker/docker-compose.yml up -d

docker-up-dev:                     ## Start full stack in dev mode (hot-reload)
	docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up -d

docker-down:                       ## Stop the stack
	docker compose -f docker/docker-compose.yml down

docker-down-dev:                   ## Stop the dev stack
	docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml down

docker-build:                      ## Rebuild the backend image (no cache)
	docker compose -f docker/docker-compose.yml build --no-cache backend

docker-logs:                       ## Tail logs from all services
	docker compose -f docker/docker-compose.yml logs -f

db-reset:                          ## Destroy and recreate the database volume
	docker compose -f docker/docker-compose.yml down -v
	docker compose -f docker/docker-compose.yml up -d postgres
	@echo "Waiting for PostgreSQL to become healthy..."
	@sleep 5
	docker compose -f docker/docker-compose.yml up -d backend
