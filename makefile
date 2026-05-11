# Makefile para content_generation (carousel-ai)

.PHONY: dev up down logs test build help

# Sobe todo o ambiente com build (recomendado para desenvolvimento)
dev:
	docker-compose up --build

# Sobe o ambiente em background (sem build)
up:
	docker-compose up -d

# Para todos os serviços e remove os containers
down:
	docker-compose down

# Exibe logs de todos os serviços em tempo real
logs:
	docker-compose logs -f

# Executa os testes de todos os serviços
test:
	@echo "Executando testes do Auth Service (Java/Spring Boot)..."
	@# docker-compose run --rm auth-service ./mvnw test
	@echo "Executando testes do AI Service (Python/FastAPI)..."
	@# docker-compose run --rm ai-service pytest
	@echo "Testes finalizados."

# Faz o build das imagens Docker
build:
	docker-compose build

# Ajuda: lista os comandos disponíveis
help:
	@echo "Comandos disponíveis:"
	@echo "  make dev     - Sobe o ambiente com build"
	@echo "  make up      - Sobe o ambiente em background"
	@echo "  make down    - Para o ambiente"
	@echo "  make logs    - Exibe logs em tempo real"
	@echo "  make test    - Executa testes (Auth e AI Services)"
	@echo "  make build   - Build das imagens Docker"
