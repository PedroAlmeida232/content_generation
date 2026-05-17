# 🌿 Git Branch Guide — carousel-ai

## Padrão de nomenclatura

```
<tipo>/<ID-TRELLO>-<descricao-em-kebab-case>
```

| Prefixo | Quando usar |
|---|---|
| `feat/` | Nova funcionalidade |
| `fix/` | Correção de bug |
| `refactor/` | Refatoração sem mudança de comportamento |
| `test/` | Testes |
| `chore/` | Config, CI, infra |
| `docs/` | Documentação |
| `ci/` | Pipeline CI/CD |
| `hotfix/` | Correção urgente em produção |

**Exemplos:**
```
feat/S1-005-jwt-service
fix/S2-006-openai-rate-limit-handler
chore/S0-005-github-actions-ci
```

---

## Estrutura de branches

```
main
 └── develop
      ├── sprint/s1-auth
      │    ├── feat/S1-005-jwt-service
      │    └── feat/S1-008-auth-register
      └── hotfix/...
```

---

## Passo a passo de uma task

```bash
# 1. Atualizar develop
git checkout develop
git pull origin develop

# 2. Entrar na branch da sprint
git checkout sprint/s1-auth
git pull origin sprint/s1-auth

# 3. Criar branch da task
git checkout -b feat/S1-005-jwt-service

# 4. Commitar
git add .
git commit -m "feat(auth): add JWT generation and validation service

Refs: S1-005"

# 5. Manter atualizado com a sprint
git fetch origin
git rebase origin/sprint/s1-auth

# 6. Push
git push -u origin feat/S1-005-jwt-service

# 7. Abrir PR: feat/S1-005-jwt-service → sprint/s1-auth

# 8. Após merge, limpar branches
git checkout sprint/s1-auth
git branch -d feat/S1-005-jwt-service
git push origin --delete feat/S1-005-jwt-service
```

---

## Padrão de commit

```
<tipo>(<escopo>): <mensagem no imperativo>

[corpo opcional]

Refs: <ID-Trello>
```

**Exemplos:**
```bash
feat(auth): add JWT generation and validation service
fix(ai): handle OpenAI RateLimitError with exponential backoff
chore(docker): add multi-stage Dockerfile for auth-service
test(auth): add integration tests for AuthController
```

**Escopos:** `auth` · `ai` · `prompt` · `frontend` · `docker` · `db` · `ci`

---

## Hotfix (urgente em produção)

```bash
git checkout main && git pull origin main
git checkout -b hotfix/descricao-do-problema

# Após corrigir:
git push -u origin hotfix/descricao-do-problema

# Abrir 2 PRs: hotfix → main  e  hotfix → develop
```

---

## Título do PR

```
[S1-005] feat(auth): add JWT generation and validation service
```