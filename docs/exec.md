# Como executar e subir os containers Docker

Este documento explica, passo a passo, como preparar o ambiente e subir os containers do projeto usando Docker.

## 1. Pré-requisitos

Antes de começar, valide se você tem os itens abaixo instalados na máquina:

- Docker Desktop ou Docker Engine
- Docker Compose
- Git
- `make` opcionalmente, caso queira usar os atalhos do `makefile`

Para conferir:

```bash
docker --version
docker-compose version
git --version
make --version
```

## 2. Acessar a raiz do projeto

Abra o terminal na pasta raiz do repositório:

```bash
cd ./content_generation
```

## 3. Criar o arquivo de variáveis de ambiente

O projeto usa um arquivo `.env` na raiz.

Se ele ainda não existir, crie a partir do modelo:

```bash
cp .env.example .env
```

No Windows PowerShell, se o comando `cp` não funcionar:

```powershell
Copy-Item .env.example .env
```

## 4. Configurar o `.env`

Abra o arquivo `.env` e revise os valores mínimos abaixo:

```env
POSTGRES_DB=Name_DB
POSTGRES_USER=USER_DB
POSTGRES_PASSWORD=PASSWORD_DB

REDIS_URL=redis://redis:6379/0

JWT_SECRET=change_this_to_a_very_long_random_string
JWT_EXPIRATION_MS=time_expiration_(seconds)

AUTH_PORT=AUTH_PORT
AI_PORT=AI_PORT
```

Recomendações:

- Troque `POSTGRES_PASSWORD` por uma senha real
- Troque `JWT_SECRET` por uma chave longa e aleatória
- Ajuste as portas se houver conflito local

## 5. Subir os containers

Existem duas formas de iniciar o ambiente.

### Opção 1: usando Make

Se `make` estiver disponível:

```bash
make dev
```

Esse comando executa o build das imagens e sobe os containers.

### Opção 2: usando Docker Compose diretamente

Se preferir usar Docker Compose sem `make`:

```bash
docker-compose up --build
```

Para subir em background:

```bash
docker-compose up -d --build
```

## 6. Containers esperados

Com a configuração atual do repositório, o `docker-compose.yml` define os seguintes serviços:

- `postgres`
- `redis`
- `auth-service`

Os nomes de container esperados são:

- `carousel-postgres`
- `carousel-redis`
- `auth-service`

## 7. Verificar se os containers subiram

Após a subida, confira os containers em execução:

```bash
docker-compose ps
```

Ou:

```bash
docker ps
```

Para acompanhar os logs:

```bash
docker-compose logs -f
```

Se estiver usando `make`:

```bash
make logs
```

## 8. Parar o ambiente

Para parar os containers:

```bash
docker-compose down
```

Ou:

```bash
make down
```

## 9. Rebuild após alterações

Se você alterar Dockerfile, dependências ou variáveis importantes, faça a subida novamente com rebuild:

```bash
docker-compose up --build
```

## 10. Observações importantes sobre o estado atual do projeto

No estado atual do repositório:

- o `docker-compose.yml` possui apenas `postgres`, `redis` e `auth-service`
- o `ai-service` e o `frontend` ainda não estão declarados nesse arquivo
- se o objetivo for subir toda a plataforma, será necessário complementar o `docker-compose.yml`

Além disso, vale revisar a configuração de variáveis do `auth-service`, porque:

- o arquivo [application.properties](content_generation\auth-service\src\main\resources\application.properties) usa `DB_URL`, `DB_USER` e `DB_PASS`
- o [docker-compose.yml](content_generation\docker-compose.yml) atualmente injeta `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`

Se o container do `auth-service` falhar ao iniciar, esse mapeamento de variáveis é o primeiro ponto a validar.

## 11. Comandos úteis

Subir com build:

```bash
docker-compose up --build
```

Subir em background:

```bash
docker-compose up -d
```

Ver status:

```bash
docker-compose ps
```

Ver logs:

```bash
docker-compose logs -f
```

Parar containers:

```bash
docker-compose down
```
