# =============================================================================
#  Makefile – Docker commands for microservices-demo
#
#  Usage: make [target]
#  Run from the project root (where docker-compose.yml lives).
#  Run 'make' or 'make help' to list targets (driven by ## comments below).
# =============================================================================

COMPOSE := docker compose

.PHONY: help up start down down-v ps ps-a status logs build build-no-cache \
        restart clean clean-all health verify rebuild up-build db-check \
        build-identity build-profile build-notification \
        logs-identity logs-profile logs-notification

# Default target: show targets and their ## description
help:
	@echo "Docker / Docker Compose targets for microservices-demo"
	@echo ""
	@grep -E '^[a-zA-Z0-9_-]+.*:.*?## ' $(MAKEFILE_LIST) | \
		awk -F':.*?## ' '{ gsub(/^[ \t]+|[ \t]+$$/, "", $$1); gsub(/^[ \t]+|[ \t]+$$/, "", $$2); printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2 }'

# -----------------------------------------------------------------------------
# Lifecycle
# -----------------------------------------------------------------------------

up start: ## Start all services (detached)
	$(COMPOSE) up -d

up-build: ## Build images and start all services
	$(COMPOSE) up -d --build

down: ## Stop and remove containers
	$(COMPOSE) down

down-v: ## Stop and remove containers and volumes
	$(COMPOSE) down -v

restart: down up ## Stop then start all services

rebuild: down ## Clean rebuild: down, build --no-cache, up (wait ~30-45s then make health)
	$(COMPOSE) build --no-cache
	$(COMPOSE) up -d
	@echo "Wait ~30-45s then run: make health"

# -----------------------------------------------------------------------------
# Build
# -----------------------------------------------------------------------------

build: ## Build all service images
	$(COMPOSE) build

build-no-cache: ## Build all images without using cache
	$(COMPOSE) build --no-cache

build-identity: ## Build identity-service image only
	$(COMPOSE) build identity-service

build-profile: ## Build profile-service image only
	$(COMPOSE) build profile-service

build-notification: ## Build notification-service image only
	$(COMPOSE) build notification-service

# -----------------------------------------------------------------------------
# Inspect
# -----------------------------------------------------------------------------

ps status: ## Show container status
	$(COMPOSE) ps

ps-a: ## Show container status including stopped
	$(COMPOSE) ps -a

logs: ## Follow all logs; use 'make logs SVC=identity-service' for one service
ifdef SVC
	$(COMPOSE) logs -f $(SVC)
else
	$(COMPOSE) logs -f
endif

logs-identity: ## Follow identity-service logs
	$(COMPOSE) logs -f identity-service

logs-profile: ## Follow profile-service logs
	$(COMPOSE) logs -f profile-service

logs-notification: ## Follow notification-service logs
	$(COMPOSE) logs -f notification-service

# -----------------------------------------------------------------------------
# Verification (health endpoints)
# -----------------------------------------------------------------------------

health verify: ## Curl /actuator/health for all three Java services
	@echo "=== identity-service (8081) ==="
	@curl -s http://localhost:8081/actuator/health | (jq . 2>/dev/null || cat)
	@echo ""
	@echo "=== profile-service (8082) ==="
	@curl -s http://localhost:8082/actuator/health | (jq . 2>/dev/null || cat)
	@echo ""
	@echo "=== notification-service (8083) ==="
	@curl -s http://localhost:8083/actuator/health | (jq . 2>/dev/null || cat)

# -----------------------------------------------------------------------------
# Database
# -----------------------------------------------------------------------------

db-check: ## Test PostgreSQL connection and verify identity_db, profile_db, notification_db exist
	@echo "Testing PostgreSQL connection..."
	@$(COMPOSE) exec -T postgres psql -U postgres -c "SELECT 1 AS connected;" >/dev/null || { echo "Error: Cannot connect to PostgreSQL. Is the stack running? (make up)"; exit 1; }
	@echo "Connection OK. Checking databases..."
	@for db in identity_db profile_db notification_db; do \
	  $(COMPOSE) exec -T postgres psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$$db'" | grep -q 1 || { echo "Error: Database '$$db' does not exist."; exit 1; }; \
	  echo "  $$db: exists"; \
	done
	@echo "All three databases exist."

# -----------------------------------------------------------------------------
# Clean
# -----------------------------------------------------------------------------

clean: down-v ## Remove containers and volumes; images are kept
	@echo "Containers and volumes removed. Images kept. Use 'docker image prune' to remove unused images."

clean-all: ## Remove containers, volumes, and images built by this project
	$(COMPOSE) down -v --rmi local
	@echo "Containers, volumes, and project images removed."
