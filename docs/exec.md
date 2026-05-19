# Como rodar as imagens e os containers

Este documento descreve o fluxo atual e validado para executar o projeto com Docker no estado real do repositório.

Hoje, os serviços utilizáveis no `docker-compose.yml` são:

- `postgres`
- `redis`
- `auth-service`

O `ai-service` e o `gateway` ainda não entram no fluxo de execução validado deste workspace. Por isso, neste momento, o comando correto é subir apenas os serviços que existem e estão prontos para uso.

## 1. Pré-requisitos

Antes de começar, confirme que sua máquina tem:

- Docker Desktop ou Docker Engine
- Docker Compose clássico via `docker-compose`
- Git

Comandos para validar:

```bash
docker --version
docker-compose version
git --version
```

## 2. Abrir a raiz do projeto

No terminal, entre na pasta raiz:

```bash
cd D:/Repositorios/content_generation
```

## 3. Criar o arquivo `.env`

Se o arquivo `.env` ainda não existir, copie o modelo:

```bash
cp .env.example .env
```

No PowerShell:

```powershell
Copy-Item .env.example .env
```

## 4. Configurar as variáveis de ambiente

Revise o `.env` e preencha os valores necessários. Exemplo:

```env
# PostgreSQL
DB_HOST=localhost
DB_PORT=5432
DB_USER=seu_usuario_postgres
DB_PASSWORD=sua_senha_segura
DB_NAME=content_generator

# Redis
REDIS_URL=redis://redis:6379/0

# JWT
JWT_SECRET=change_this_to_a_very_long_random_string
JWT_EXPIRATION_MS=time_expiration_(seconds)

# Portas dos serviços
AUTH_PORT=8080
AI_PORT=8000
```

Notas importantes:

- No host, `DB_HOST=localhost` está correto para referência humana e para execuções fora do Docker.
- Dentro do container do `auth-service`, o Compose sobrescreve `DB_HOST` para `postgres`, que é o nome do serviço na rede Docker.
- O `auth-service` publica a porta `${AUTH_PORT}` no host. Se `AUTH_PORT=8080`, o endpoint ficará acessível em `http://localhost:8080`.

## 5. Build e subida dos containers

Para o estado atual do projeto, suba apenas os serviços existentes:

```bash
docker-compose up --build --force-recreate postgres redis auth-service
```

Para rodar em background:

```bash
docker-compose up -d --build --force-recreate postgres redis auth-service
```

O que esse comando faz:

- sobe o PostgreSQL
- sobe o Redis
- faz build da imagem do `auth-service`
- recria os containers
- publica a porta do `auth-service` no host

## 6. Como saber se funcionou

Os sinais esperados nos logs do `auth-service` são:

- `HikariPool-1 - Start completed.`
- `Tomcat started on port 8080 (http)`
- `Started AuthServiceApplication`

Para acompanhar os logs:

```bash
docker-compose logs -f auth-service
```

Para ver o status dos containers:

```bash
docker-compose ps
```

## 7. Testar o endpoint `/health`

Com o `auth-service` no ar, teste:

```bash
curl http://localhost:8080/health
```

Resposta esperada:

```json
{"status":"ok"}
```

Você também pode testar no navegador ou no Postman:

- URL: `http://localhost:8080/health`
- Método: `GET`

## 8. Rodando apenas a imagem do `auth-service`

Embora seja possível usar `docker run`, isso não é o fluxo recomendado para este projeto, porque o `auth-service` depende do banco e das variáveis que o Compose já organiza.

Se você insistir em rodar a imagem isoladamente, precisará:

- subir um PostgreSQL separado
- garantir que a rede Docker permita acesso entre os containers
- passar manualmente `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` e `DB_NAME`

Na prática, para este repositório, prefira sempre:

```bash
docker-compose up --build postgres redis auth-service
```

## 9. Problemas comuns

### 9.1. `ECONNREFUSED 127.0.0.1:8080`

Isso normalmente significa que:

- o `auth-service` não subiu
- ou a porta ainda não foi publicada no host

Verifique:

```bash
docker-compose ps
docker-compose logs -f auth-service
```

### 9.2. `password authentication failed for user`

Isso geralmente acontece quando o volume do PostgreSQL foi criado com credenciais antigas.

O Compose atual usa um volume nomeado:

- `content_generation_postgres_data_v2`

Se as credenciais mudarem e o banco ficar inconsistente, derrube o ambiente e recrie os containers:

```bash
docker-compose down
docker-compose up --build --force-recreate postgres redis auth-service
```

Se ainda assim houver conflito de dados antigos, pode ser necessário remover o volume explicitamente:

```bash
docker volume rm content_generation_postgres_data_v2
docker-compose up --build --force-recreate postgres redis auth-service
```

Atenção:

- remover o volume apaga os dados persistidos do banco nesse ambiente local

### 9.3. `unable to prepare context: path ".../ai-service" not found`

Esse erro aparece quando o Compose tenta subir `ai-service`, mas essa pasta não existe no repositório atual.

Para evitar o problema, suba apenas:

```bash
docker-compose up --build postgres redis auth-service
```

## 10. Parar o ambiente

Para parar os containers:

```bash
docker-compose down
```

Para parar e remover também volumes órfãos do Compose:

```bash
docker-compose down -v
```

## 11. Comandos úteis

Subir com build:

```bash
docker-compose up --build postgres redis auth-service
```

Subir em background:

```bash
docker-compose up -d --build postgres redis auth-service
```

Ver status:

```bash
docker-compose ps
```

Ver logs do `auth-service`:

```bash
docker-compose logs -f auth-service
```

Testar health check:

```bash
curl http://localhost:8080/health
```

Parar tudo:

```bash
docker-compose down
```
