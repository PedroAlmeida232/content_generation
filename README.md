# content_generation
# 🎠 carousel-ai

> Plataforma de criação de carrosséis para Instagram baseados em IA — monorepo com autenticação Java/Spring Boot, geração de imagens Python/FastAPI e frontend HTML/CSS/JS.

---

## Índice

- [Visão Geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Estrutura de Diretórios](#estrutura-de-diretórios)
- [Serviços](#serviços)
  - [Auth Service (Java/Spring Boot)](#auth-service-javaspring-boot)
  - [AI Service (Python/FastAPI)](#ai-service-pythonfastapi)
  - [Frontend (HTML/CSS/JS)](#frontend-htmlcssjs)
  - [Infraestrutura](#infraestrutura)
- [Fluxo de Dados](#fluxo-de-dados)
- [Endpoints REST](#endpoints-rest)
- [Modelagem de Dados](#modelagem-de-dados)
- [Ferramentas e Bibliotecas](#ferramentas-e-bibliotecas)
- [Estratégia de Comunicação](#estratégia-de-comunicação)
- [Configuração do Ambiente](#configuração-do-ambiente)
- [Variáveis de Ambiente](#variáveis-de-ambiente)
- [Roadmap por Sprints](#roadmap-por-sprints)

---

## Visão Geral

O **carousel-ai** permite que criadores de conteúdo gerem carrosséis para Instagram de forma automatizada. O usuário configura seu contexto de marca (logotipo, paleta de cores, tom de voz), descreve o conteúdo desejado, escolhe um estilo visual e define o número de slides — a IA cuida do resto, gerando texto e imagens via GPT-4 e DALL-E 3.

### Principais funcionalidades

- Autenticação segura com JWT
- Persistência de contexto de marca por usuário (paleta, logo, estilo)
- Geração assíncrona de carrosséis via OpenAI (GPT-4 + DALL-E 3)
- Suporte a múltiplos estilos visuais (Clássico, Futurista, Cartoon, Minimalista, etc.)
- API Key do usuário nunca armazenada — usada apenas em memória por requisição
- Histórico de projetos e slides gerados

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                         Frontend                            │
│              HTML / CSS / JS  (Nginx :80)                   │
└────────────────────┬──────────────────┬────────────────────┘
                     │                  │
          /api/auth/*│                  │/api/ai/*
                     ▼                  ▼
         ┌───────────────┐    ┌──────────────────┐
         │  Auth Service │    │    AI Service     │
         │  Java/Spring  │    │  Python/FastAPI   │
         │   Boot :8080  │    │     :8000         │
         └──────┬────────┘    └────────┬──────────┘
                │                      │
                ▼                      ▼
         ┌─────────────┐      ┌──────────────────┐
         │ PostgreSQL  │      │  Celery + Redis   │
         │   :5432     │      │  (fila async)     │
         └─────────────┘      └────────┬──────────┘
                                        │
                                        ▼
                               ┌─────────────────┐
                               │   OpenAI API    │
                               │ GPT-4 + DALL-E  │
                               └─────────────────┘
```

### Decisões arquiteturais

| Decisão | Escolha | Justificativa |
|---|---|---|
| Separação de serviços | Auth Java / AI Python | Cada linguagem no que faz melhor: Java para segurança/persistência, Python para ecossistema de IA |
| Geração assíncrona | Celery + Redis | DALL-E pode levar 15–45s por imagem — HTTP síncrono causaria timeout |
| JWT compartilhado | Segredo em comum | FastAPI valida o token localmente sem chamada de rede ao Java |
| API Key do usuário | Header `X-OpenAI-Key` | Nunca persiste no servidor — privacidade total do usuário |
| Gateway único | Nginx reverse proxy | Frontend consome uma única origem; CORS centralizado |

---

## Estrutura de Diretórios

```
carousel-ai/
│
├── auth-service/                  # Backend Java/Spring Boot
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   ├── config/
│   │       │   │   ├── SecurityConfig.java
│   │       │   │   └── JwtConfig.java
│   │       │   ├── controller/
│   │       │   │   ├── AuthController.java
│   │       │   │   ├── UserController.java
│   │       │   │   └── ContextController.java
│   │       │   ├── domain/
│   │       │   │   ├── User.java
│   │       │   │   ├── UserContext.java
│   │       │   │   └── Project.java
│   │       │   ├── repository/
│   │       │   ├── service/
│   │       │   ├── security/       # JWT filters e handlers
│   │       │   └── dto/
│   │       └── resources/
│   │           ├── application.yml
│   │           └── db/migration/   # Flyway migrations
│   ├── Dockerfile
│   └── pom.xml
│
├── ai-service/                    # Backend Python/FastAPI
│   ├── app/
│   │   ├── api/
│   │   │   └── routes/
│   │   │       ├── generate.py
│   │   │       └── health.py
│   │   ├── core/
│   │   │   ├── config.py
│   │   │   └── security.py        # Validação JWT (python-jose)
│   │   ├── schemas/
│   │   │   └── carousel.py        # Pydantic models
│   │   ├── services/
│   │   │   ├── prompt_builder.py  # LangChain PromptTemplates
│   │   │   ├── openai_client.py
│   │   │   └── image_processor.py # Pillow: resize/compose
│   │   ├── tasks/
│   │   │   └── generate_task.py   # Celery tasks
│   │   └── main.py
│   ├── requirements.txt
│   ├── Dockerfile
│   └── celery_worker.py
│
├── frontend/                      # Interface HTML/CSS/JS
│   ├── src/
│   │   ├── pages/
│   │   │   ├── login.html
│   │   │   ├── dashboard.html
│   │   │   ├── editor.html        # Criação do carrossel
│   │   │   └── projects.html
│   │   ├── js/
│   │   │   ├── auth.js
│   │   │   ├── apiClient.js       # Wrapper Axios/fetch
│   │   │   ├── carouselEditor.js
│   │   │   └── storage.js         # API key no localStorage
│   │   ├── css/
│   │   │   ├── design-tokens.css
│   │   │   └── components.css
│   │   └── assets/
│   ├── index.html
│   └── nginx.conf
│
├── infra/                         # Infraestrutura
│   ├── docker/
│   │   ├── docker-compose.yml
│   │   └── docker-compose.prod.yml
│   ├── nginx/
│   │   └── gateway.conf           # Reverse proxy
│   ├── postgres/
│   │   └── init.sql
│   └── redis/
│       └── redis.conf
│
├── .github/
│   └── workflows/
│       └── ci.yml
│
├── docker-compose.yml             # Compose raiz (desenvolvimento)
├── .env.example
├── Makefile                       # make dev | make test | make build
└── README.md
```

---

## Serviços

### Auth Service (Java/Spring Boot)

Responsável por gerenciamento de usuários, persistência de contextos de marca e emissão de tokens JWT.

**Stack:** Java 17, Spring Boot 3, Spring Security, Spring Data JPA, Flyway, PostgreSQL

**Responsabilidades:**
- Registro e autenticação de usuários
- Emissão e validação de tokens JWT
- CRUD de contextos de marca (paleta de cores, logotipo, imagens padrão)
- CRUD de projetos (metadados dos carrosséis gerados)

**Porta:** `8080`

---

### AI Service (Python/FastAPI)

Responsável por receber o contexto e a descrição do post, montar prompts estruturados via LangChain e realizar chamadas à API da OpenAI.

**Stack:** Python 3.11, FastAPI, LangChain, Celery, Redis, Pillow, python-jose

**Responsabilidades:**
- Validação do JWT recebido do Frontend
- Recebimento da API Key do usuário via header (nunca persiste)
- Construção de prompts com LangChain PromptTemplate
- Enfileiramento de jobs de geração no Celery/Redis
- Chamadas assíncronas ao GPT-4 (textos) e DALL-E 3 (imagens)
- Pós-processamento de imagens (redimensionamento para formatos Instagram)
- Endpoint de polling para status dos jobs

**Porta:** `8000`

---

### Frontend (HTML/CSS/JS)

Interface limpa e responsiva para criação dos carrosséis.

**Stack:** HTML5, CSS3, JavaScript ES6+, Nginx

**Telas:**
- `/login` — Autenticação do usuário
- `/dashboard` — Visão geral dos projetos
- `/editor` — Criação do carrossel (prompt, estilo, nº de slides, API key)
- `/projects` — Histórico de carrosséis gerados

**Porta:** `80` (via Nginx)

---

### Infraestrutura

| Serviço | Uso | Porta |
|---|---|---|
| PostgreSQL 15 | Persistência principal (users, contexts, projects) | 5432 |
| Redis 7 | Broker do Celery + result backend | 6379 |
| Nginx | Reverse proxy + serve frontend estático | 80 |

---

## Fluxo de Dados

### 1. Autenticação

```
Frontend → POST /api/auth/login {email, password}
        → Java valida credenciais no PostgreSQL
        → Retorna JWT (payload: userId, email, exp)
        → Frontend armazena JWT no localStorage
```

### 2. Configuração de contexto

```
Frontend → POST /api/auth/contexts {name, logo_url, color_palette, tone}
         → Java persiste no PostgreSQL (tabela user_contexts)
         → Retorna context_id para uso na geração
```

### 3. Geração do carrossel

```
Frontend → POST /api/ai/generate/carousel
           Headers: Authorization: Bearer {jwt}
                    X-OpenAI-Key: sk-...
           Body: {context_id, prompt, style, slide_count}

        → FastAPI valida JWT localmente (python-jose)
        → FastAPI extrai X-OpenAI-Key do header (NUNCA salva)
        → Celery enfileira task no Redis
        → Retorna imediatamente: {job_id, status: "pending"}
```

### 4. Processamento assíncrono (Celery Worker)

```
Worker consome job da fila Redis
  → LangChain monta prompt com contexto do usuário
  → Chama GPT-4: gera textos/legendas para cada slide
  → Chama DALL-E 3: gera imagem para cada slide
  → Pillow: redimensiona para formato Instagram (1:1, 4:5 ou 9:16)
  → Salva URLs dos resultados no Redis (result backend)
  → Atualiza status para "done"
```

### 5. Polling e resultado

```
Frontend → GET /api/ai/jobs/{job_id}    (a cada 3 segundos)
         → FastAPI consulta Redis
         → Enquanto pending: retorna {status: "processing", progress: N}
         → Quando done: retorna {status: "done", slides: [{image_url, caption}]}
         → Frontend renderiza preview do carrossel
```

### Segurança da API Key

```
┌─────────────┐   X-OpenAI-Key: sk-...   ┌─────────────┐
│  Frontend   │ ─────────────────────────▶│  FastAPI    │
│ localStorage│                           │  (memória)  │
└─────────────┘                           └──────┬──────┘
                                                  │ usa em memória
                                                  ▼
                                         ┌─────────────────┐
                                         │   OpenAI API    │
                                         └─────────────────┘
                                                  │ descarta após resposta
                                                  ▼
                                         ❌ Nunca persiste no Redis
                                         ❌ Nunca persiste no Postgres
```

---

## Endpoints REST

### Auth Service — Java (:8080)

| Método | Endpoint | Descrição | Auth |
|---|---|---|---|
| `POST` | `/auth/register` | Criar nova conta | ❌ |
| `POST` | `/auth/login` | Autenticar e obter JWT | ❌ |
| `POST` | `/auth/refresh` | Renovar token expirado | ✅ |
| `GET` | `/users/me` | Perfil do usuário autenticado | ✅ |
| `PUT` | `/users/me` | Atualizar dados do perfil | ✅ |
| `GET` | `/contexts` | Listar contextos de marca | ✅ |
| `POST` | `/contexts` | Salvar novo contexto | ✅ |
| `GET` | `/contexts/{id}` | Detalhe de um contexto | ✅ |
| `PUT` | `/contexts/{id}` | Editar contexto | ✅ |
| `DELETE` | `/contexts/{id}` | Remover contexto | ✅ |
| `GET` | `/projects` | Listar projetos do usuário | ✅ |
| `POST` | `/projects` | Criar novo projeto | ✅ |
| `GET` | `/projects/{id}` | Detalhe e slides do projeto | ✅ |
| `DELETE` | `/projects/{id}` | Remover projeto | ✅ |

### AI Service — Python (:8000)

| Método | Endpoint | Descrição | Headers obrigatórios |
|---|---|---|---|
| `POST` | `/generate/carousel` | Enfileira geração completa | JWT + X-OpenAI-Key |
| `POST` | `/generate/preview` | Gera apenas 1 slide (preview rápido) | JWT + X-OpenAI-Key |
| `GET` | `/jobs/{job_id}` | Status do job (polling) | JWT |
| `GET` | `/jobs/{job_id}/result` | URLs e metadados do resultado | JWT |
| `POST` | `/prompts/build` | Retorna o prompt montado sem chamar a OpenAI (debug) | JWT |
| `GET` | `/styles` | Lista estilos visuais disponíveis | ❌ |
| `GET` | `/health` | Liveness probe | ❌ |

---

## Modelagem de Dados

### Tabela `users`

```sql
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(255),
    is_active     BOOLEAN DEFAULT TRUE,
    created_at    TIMESTAMP DEFAULT now(),
    updated_at    TIMESTAMP DEFAULT now()
);
```

### Tabela `user_contexts`

```sql
CREATE TABLE user_contexts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,         -- ex: "Marca Principal"
    logo_url       TEXT,
    color_palette  JSONB,                          -- ["#FF5733", "#C70039", ...]
    default_images JSONB,                          -- URLs de imagens padrão
    tone           VARCHAR(100),                   -- ex: "profissional", "descontraído"
    created_at     TIMESTAMP DEFAULT now()
);
```

### Tabela `projects`

```sql
CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    context_id  UUID REFERENCES user_contexts(id),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    style       VARCHAR(100),          -- "Clássico", "Futurista", "Cartoon"...
    slide_count INTEGER DEFAULT 5,
    status      VARCHAR(50) DEFAULT 'draft',  -- draft | generating | done | failed
    job_id      VARCHAR(255),          -- referência ao job Celery
    created_at  TIMESTAMP DEFAULT now()
);
```

### Tabela `project_slides`

```sql
CREATE TABLE project_slides (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    slide_order  INTEGER NOT NULL,
    image_url    TEXT,
    caption      TEXT,
    prompt_used  TEXT,                 -- prompt exato enviado ao DALL-E
    generated_at TIMESTAMP DEFAULT now()
);
```

### Índices recomendados

```sql
CREATE INDEX idx_user_contexts_user_id ON user_contexts(user_id);
CREATE INDEX idx_projects_user_id      ON projects(user_id);
CREATE INDEX idx_projects_status       ON projects(status);
CREATE INDEX idx_project_slides_project ON project_slides(project_id, slide_order);
```

> **Evolução futura:** adicionar `embedding vector(1536)` na tabela `user_contexts` com a extensão `pgvector` para busca semântica de contextos similares.

---

## Ferramentas e Bibliotecas

### Python / AI Service

| Biblioteca | Versão | Finalidade |
|---|---|---|
| `fastapi` | `^0.111` | Framework web async |
| `pydantic` | `v2` | Validação de schemas de entrada/saída |
| `langchain` | `^0.2` | PromptTemplates e integração OpenAI |
| `langchain-openai` | `^0.1` | ChatOpenAI + DallEAPIWrapper |
| `celery` | `^5.4` | Processamento assíncrono de tasks |
| `redis` | `^5.0` | Broker e result backend do Celery |
| `python-jose` | `^3.3` | Validação local do JWT emitido pelo Java |
| `Pillow` | `^10.3` | Resize e composição de imagens |
| `httpx` | `^0.27` | Cliente HTTP async para chamadas internas |
| `uvicorn` | `^0.30` | Servidor ASGI |

### Java / Auth Service

| Dependência | Finalidade |
|---|---|
| `spring-boot-starter-security` | Segurança e filtros de autenticação |
| `spring-boot-starter-data-jpa` | ORM com Hibernate |
| `spring-boot-starter-web` | API REST |
| `jjwt-api` / `jjwt-impl` | Geração e validação de JWT |
| `flyway-core` | Migrações de banco de dados |
| `postgresql` | Driver JDBC |
| `lombok` | Redução de boilerplate |
| `mapstruct` | Mapeamento Entity ↔ DTO |

---

## Estratégia de Comunicação

### Sync vs Async

```
Operações síncronas (resposta imediata):
  - Login / registro
  - CRUD de contextos e projetos
  - Listar projetos
  - /generate/preview (1 slide, aceita ~5s de espera)

Operações assíncronas (Celery):
  - /generate/carousel (múltiplos slides, 30–120s)
  - Pós-processamento de imagens
```

### Compartilhamento de JWT

O Java Auth Service e o Python AI Service compartilham o mesmo `JWT_SECRET`. O FastAPI valida o token localmente com `python-jose` — sem chamada de rede ao Java em cada request, mantendo os serviços desacoplados.

### Nginx Gateway

```nginx
# infra/nginx/gateway.conf

location /api/auth/ {
    proxy_pass http://auth-service:8080/;
}

location /api/ai/ {
    proxy_pass http://ai-service:8000/;
}

location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;
}
```

---

## Configuração do Ambiente

### Pré-requisitos

- Docker 24+ e Docker Compose v2
- Make

### Subir o ambiente de desenvolvimento

```bash
# Clone o repositório
git clone https://github.com/seu-usuario/carousel-ai.git
cd carousel-ai

# Copie e configure as variáveis de ambiente
cp .env.example .env

# Suba todos os serviços
make dev
```

O comando `make dev` executa `docker compose up --build` na raiz do monorepo, subindo PostgreSQL, Redis, auth-service, ai-service e frontend.

### Comandos Makefile

```bash
make dev        # Sobe todo o ambiente (com build)
make up         # Sobe sem rebuild
make down       # Para todos os serviços
make logs       # Logs de todos os serviços
make test       # Executa testes (Java + Python)
make build      # Build de produção
make migrate    # Executa migrações Flyway
make lint       # Linting (Checkstyle + Ruff)
```

---

## Variáveis de Ambiente

Copie `.env.example` para `.env` e preencha os valores:

```env
# ─── Banco de Dados ───────────────────────────────────────────
POSTGRES_DB=carousel_ai
POSTGRES_USER=carousel_user
POSTGRES_PASSWORD=sua_senha_segura
DATABASE_URL=postgresql://carousel_user:sua_senha_segura@postgres:5432/carousel_ai

# ─── JWT (deve ser idêntico no Java e no Python) ──────────────
JWT_SECRET=seu_segredo_jwt_longo_e_aleatorio_minimo_256bits
JWT_EXPIRATION_MS=86400000

# ─── Redis ────────────────────────────────────────────────────
REDIS_URL=redis://redis:6379/0

# ─── Auth Service ─────────────────────────────────────────────
AUTH_PORT=8080
SPRING_PROFILES_ACTIVE=dev

# ─── AI Service ───────────────────────────────────────────────
AI_PORT=8000
# NOTA: A OpenAI API Key NÃO vai aqui.
# Ela é enviada pelo usuário via header X-OpenAI-Key a cada request.

# ─── Frontend ─────────────────────────────────────────────────
VITE_AUTH_API_URL=/api/auth
VITE_AI_API_URL=/api/ai
```

---

## Roadmap por Sprints

> Cada sprint tem duração de **2 semanas**. O projeto está organizado em 5 sprints para entrega do MVP funcional, seguidos de 2 sprints de evolução pós-MVP.

---

### Sprint 0 — Fundação e Setup (Semana 0–1)

**Objetivo:** Monorepo configurado, ambiente local rodando, CI básico no ar.

#### Tarefas

**Infra e DevOps**
- [ ] Criar repositório no GitHub com estrutura de monorepo
- [ ] Criar `docker-compose.yml` raiz com PostgreSQL e Redis
- [ ] Criar `Makefile` com targets: `dev`, `down`, `logs`, `test`, `build`
- [ ] Criar `.env.example` documentado com todas as variáveis
- [ ] Configurar GitHub Actions (`ci.yml`) com steps de build e lint
- [ ] Configurar Nginx como gateway com rotas `/api/auth/*` e `/api/ai/*`

**Auth Service (Java)**
- [ ] Inicializar projeto Spring Boot com dependências (Security, JPA, Flyway, JWT)
- [ ] Configurar conexão com PostgreSQL via `application.yml`
- [ ] Criar primeira migration Flyway (`V1__create_users_table.sql`)
- [ ] Criar `Dockerfile` multi-stage para produção
- [ ] Endpoint `GET /health` retornando `200 OK`

**AI Service (Python)**
- [ ] Inicializar projeto FastAPI com `requirements.txt`
- [ ] Configurar Celery com Redis como broker
- [ ] Criar `Dockerfile` com uvicorn
- [ ] Endpoint `GET /health` retornando `200 OK`
- [ ] Endpoint `GET /styles` retornando lista hardcoded de estilos

**Frontend**
- [ ] Criar estrutura de pastas `pages/`, `js/`, `css/`, `assets/`
- [ ] Criar `design-tokens.css` com variáveis de cor, tipografia e espaçamento
- [ ] Criar página `login.html` com layout base (sem lógica ainda)
- [ ] Configurar `nginx.conf` para servir arquivos estáticos

**Entregável:** `make dev` sobe todo o ambiente; todos os `/health` respondem `200`.
