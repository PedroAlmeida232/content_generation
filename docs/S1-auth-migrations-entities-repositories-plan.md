# Plano detalhado para concluir as tasks S1-001 a S1-004 no auth-service

## Objetivo

Este documento descreve um plano técnico detalhado para implementar as entregas pendentes do `auth-service` relacionadas a persistência, modelagem JPA e acesso a dados.

As tasks cobertas aqui são:

- `S1-001` Criar `V2__create_user_contexts_table.sql`
- `S1-002` Criar `V3__create_projects_table.sql`
- `S1-003` Criar entidades JPA `User`, `UserContext` e `Project`
- `S1-004` Criar `UserRepository`, `UserContextRepository` e `ProjectRepository`

O plano parte do estado real do repositório em `2026-05-21`, no qual já existe:

- Spring Data JPA configurado no `pom.xml`
- Flyway configurado no `pom.xml`
- PostgreSQL configurado no projeto
- a migration `V1__create_users_table.sql`

Também parte do fato de que o `README` descreve uma estrutura alvo para o `auth-service`, mas essa estrutura ainda não foi implementada completamente no código.

## Estado atual validado

Hoje, o módulo `auth-service` já possui dependências para persistência e migração:

- `spring-boot-starter-data-jpa`
- `spring-boot-starter-flyway`
- `flyway-database-postgresql`
- driver `postgresql`

No banco, há apenas a tabela `users`, definida pela migration `V1__create_users_table.sql`, com os seguintes campos:

- `id`
- `email`
- `password_hash`
- `name`
- `is_active`

Isso significa que o projeto já tem a base necessária para evoluir o modelo relacional, mas ainda faltam:

- tabelas relacionadas a contexto do usuário e projetos
- classes de domínio JPA
- repositories Spring Data
- validação de consistência entre modelo Java e schema SQL

## Resultado esperado ao final

Ao concluir esse plano, o `auth-service` deve ter:

1. Schema versionado por Flyway com as migrations `V1`, `V2` e `V3`.
2. Entidades JPA representando `users`, `user_contexts` e `projects`.
3. Relacionamentos entre entidades corretamente mapeados.
4. Repositories Spring Data para acesso às três entidades.
5. Build do módulo compilando sem erro.
6. Contexto Spring capaz de subir sem falhas de mapeamento JPA ou Flyway.

## Premissas de modelagem

Como as tasks não vieram acompanhadas de um modelo funcional detalhado, este plano adota premissas conservadoras para evitar retrabalho e acoplamento excessivo.

### Premissa 1: `users` continua sendo a entidade raiz de identidade

A tabela `users` já existe e deve permanecer como a raiz de autenticação e identificação de conta.

Responsabilidades esperadas de `User`:

- armazenar identidade do usuário
- armazenar credenciais
- servir como agregado pai para contextos e projetos

### Premissa 2: `user_contexts` pertence a um usuário

O nome da task sugere que contexto é um recurso ligado ao usuário, não uma entidade global.

Por isso, a modelagem recomendada é:

- um `User` pode ter vários `UserContext`
- cada `UserContext` pertence a exatamente um `User`

Relacionamento esperado:

- `User 1:N UserContext`

### Premissa 3: `projects` deve ter dono explícito

Há duas modelagens possíveis:

1. projeto ligado diretamente ao usuário
2. projeto ligado ao contexto do usuário

Como não há evidência no repositório de que um projeto dependa estruturalmente de um contexto específico, a opção mais segura para esta sprint é:

- `Project` pertence diretamente a `User`

Essa escolha reduz complexidade inicial, facilita consultas e evita obrigar toda operação de projeto a depender da existência prévia de um contexto.

Relacionamento esperado:

- `User 1:N Project`

Se futuramente o domínio exigir vínculo entre projeto e contexto, isso pode ser feito em nova migration, sem bloquear a entrega atual.

## Decisões técnicas recomendadas

### 1. Padronizar UUID como chave primária

A tabela `users` já usa UUID com `gen_random_uuid()`. O ideal é manter o mesmo padrão em `user_contexts` e `projects` para consistência operacional e de modelagem.

Benefícios:

- uniformidade entre tabelas
- menor risco de colisão em ambientes distribuídos
- simplicidade de serialização entre serviços

### 2. Adicionar timestamps nas novas tabelas

Mesmo que a task não exija explicitamente, é tecnicamente recomendável incluir:

- `created_at`
- `updated_at`

Justificativa:

- facilita auditoria básica
- simplifica troubleshooting
- reduz necessidade de alterações futuras só para rastreabilidade

Se o time quiser aderência estrita ao mínimo escopo da sprint, esses campos podem ser omitidos. Ainda assim, a recomendação técnica é incluí-los agora.

### 3. Definir nomes SQL em snake_case e Java em camelCase

Padrão recomendado:

- tabelas em `snake_case`
- colunas em `snake_case`
- classes e atributos Java em convenção idiomática do ecossistema

Isso ajuda a manter legibilidade dos dois lados sem depender de convenções implícitas frágeis.

### 4. Evitar cascades agressivos na primeira versão

Para a primeira implementação, recomenda-se:

- `@ManyToOne(fetch = FetchType.LAZY)` nas entidades filhas
- `@OneToMany(mappedBy = ...)` na entidade pai
- evitar `CascadeType.ALL` por padrão

Motivo:

- reduz risco de deleções inesperadas
- evita efeitos colaterais em operações simples
- deixa regras de ciclo de vida explícitas

### 5. Criar apenas queries derivadas necessárias

Os repositories devem começar simples, usando `JpaRepository`.

Exemplos úteis e prováveis:

- `Optional<User> findByEmail(String email)`
- `List<UserContext> findByUserId(UUID userId)`
- `List<Project> findByUserId(UUID userId)`

Não vale a pena antecipar consultas customizadas sem uso real.

## Plano detalhado por task

## S1-001 - Criar `V2__create_user_contexts_table.sql`

### Objetivo

Adicionar uma tabela para armazenar contextos relacionados a um usuário.

### Estrutura recomendada

Campos mínimos sugeridos:

- `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- `user_id UUID NOT NULL`
- `context_key VARCHAR(100) NOT NULL`
- `context_value TEXT`
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

### Constraints recomendadas

- foreign key de `user_id` para `users(id)`
- índice em `user_id`
- constraint única composta em `user_id + context_key` se a regra de negócio for um contexto por chave

### Fundamentação

Sem uma chave lógica, o repositório pode acabar armazenando múltiplas entradas ambíguas para o mesmo tipo de contexto. A combinação `user_id + context_key` cria previsibilidade sem engessar demais.

### Critérios de aceite

- migration criada em `src/main/resources/db/migration/`
- nome exatamente versionado como `V2__create_user_contexts_table.sql`
- tabela criada com chave primária UUID
- foreign key válida para `users`
- migration executável pelo Flyway sem erro

## S1-002 - Criar `V3__create_projects_table.sql`

### Objetivo

Adicionar uma tabela para projetos pertencentes a usuários.

### Estrutura recomendada

Campos mínimos sugeridos:

- `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- `user_id UUID NOT NULL`
- `name VARCHAR(255) NOT NULL`
- `description TEXT`
- `status VARCHAR(50)`
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

### Constraints recomendadas

- foreign key de `user_id` para `users(id)`
- índice em `user_id`
- índice opcional em `status`
- unique opcional em `user_id + name` se o domínio exigir nome único por usuário

### Fundamentação

`Project` normalmente é um agregado funcional mais estável e consultado por dono. Por isso, o campo `user_id` precisa ser indexado desde o início.

O campo `status` é opcional, mas útil se o projeto já nasce com ciclo de vida simples, por exemplo:

- `ACTIVE`
- `ARCHIVED`
- `DRAFT`

Se o time quiser escopo mínimo, ele pode ser omitido nesta sprint.

### Critérios de aceite

- migration criada com nome `V3__create_projects_table.sql`
- tabela criada com UUID
- foreign key para `users`
- índices básicos adicionados
- ordem de versionamento Flyway consistente com `V1` e `V2`

## S1-003 - Criar entidades JPA `User`, `UserContext` e `Project`

### Objetivo

Representar o schema relacional no domínio Java com mapeamento JPA consistente.

### Estrutura recomendada de pacotes

Padrão sugerido:

- `com.example.auth_service.domain.User`
- `com.example.auth_service.domain.UserContext`
- `com.example.auth_service.domain.Project`

Isso mantém aderência ao `README` e separa claramente o domínio das camadas de controller, service e repository.

### Entidade `User`

Responsabilidades:

- mapear a tabela `users`
- expor relacionamentos com `UserContext` e `Project`

Campos esperados:

- `id`
- `email`
- `passwordHash`
- `name`
- `isActive`

Relacionamentos:

- `List<UserContext> contexts`
- `List<Project> projects`

Decisões recomendadas:

- usar `@Table(name = "users")`
- mapear `password_hash` explicitamente
- evitar expor `passwordHash` em serialização futura

### Entidade `UserContext`

Responsabilidades:

- mapear a tabela `user_contexts`
- representar um contexto pertencente a um usuário

Campos esperados:

- `id`
- `contextKey`
- `contextValue`
- `createdAt`
- `updatedAt`
- `user`

Decisões recomendadas:

- usar `@ManyToOne(fetch = FetchType.LAZY)`
- usar `@JoinColumn(name = "user_id", nullable = false)`

### Entidade `Project`

Responsabilidades:

- mapear a tabela `projects`
- representar um projeto associado a um usuário

Campos esperados:

- `id`
- `name`
- `description`
- `status`
- `createdAt`
- `updatedAt`
- `user`

Decisões recomendadas:

- `@ManyToOne(fetch = FetchType.LAZY)`
- `@JoinColumn(name = "user_id", nullable = false)`

### Considerações de implementação

#### UUID

Se o projeto estiver em Spring Boot moderno com Hibernate compatível, é possível usar `UUID` diretamente como tipo Java.

#### Lombok

Como Lombok já está no `pom.xml`, pode ser usado para reduzir boilerplate. Mesmo assim, é importante evitar combinações problemáticas como:

- `@Data` em entidades com relacionamentos bidirecionais

Recomendação:

- preferir `@Getter` e `@Setter`
- adicionar `@NoArgsConstructor` e `@AllArgsConstructor` apenas se realmente necessário
- controlar `equals` e `hashCode` com cuidado

#### Equals e hashCode

Não é recomendável gerar `equals` e `hashCode` com todos os campos em entidades JPA com coleções e relacionamentos bidirecionais. A abordagem mais segura é:

- basear identidade primariamente no `id`
- ou omitir implementação customizada nesta primeira entrega

### Critérios de aceite

- classes compilam
- nomes de tabela e coluna batem com as migrations
- relacionamentos estão consistentes nos dois lados
- contexto Spring inicializa sem erro de metamodelo

## S1-004 - Criar repositories `UserRepository`, `UserContextRepository` e `ProjectRepository`

### Objetivo

Disponibilizar acesso a dados por meio de Spring Data JPA, com interfaces simples, previsíveis e extensíveis.

### Estrutura recomendada

Padrão sugerido:

- `com.example.auth_service.repository.UserRepository`
- `com.example.auth_service.repository.UserContextRepository`
- `com.example.auth_service.repository.ProjectRepository`

### Assinaturas recomendadas

#### `UserRepository`

- estender `JpaRepository<User, UUID>`
- adicionar `Optional<User> findByEmail(String email)`
- adicionar `boolean existsByEmail(String email)` se cadastro/login já forem próximos passos

#### `UserContextRepository`

- estender `JpaRepository<UserContext, UUID>`
- adicionar `List<UserContext> findByUserId(UUID userId)`
- adicionar `Optional<UserContext> findByUserIdAndContextKey(UUID userId, String contextKey)` se houver unicidade por chave

#### `ProjectRepository`

- estender `JpaRepository<Project, UUID>`
- adicionar `List<Project> findByUserId(UUID userId)`

### Fundamentação

Essas consultas cobrem os caminhos mais prováveis do domínio inicial:

- busca de usuário por email
- listagem de contextos por usuário
- listagem de projetos por usuário

Isso entrega valor imediato sem adicionar complexidade prematura.

### Critérios de aceite

- interfaces compilam
- Spring detecta os repositories automaticamente
- tipos genéricos correspondem às entidades corretas

## Sequência recomendada de implementação

Para reduzir erro e retrabalho, a ordem recomendada é:

1. Validar o desenho final das tabelas novas.
2. Criar `V2__create_user_contexts_table.sql`.
3. Criar `V3__create_projects_table.sql`.
4. Implementar `User`.
5. Implementar `UserContext`.
6. Implementar `Project`.
7. Implementar os três repositories.
8. Rodar build e testes.
9. Ajustar documentação.

Essa ordem é importante porque o mapeamento JPA deve refletir o schema real, e não o contrário.

## Riscos e pontos de atenção

## 1. Divergência entre migration e entidade

Esse é o risco principal. Exemplos comuns:

- coluna `password_hash` no SQL e `passwordHash` sem `@Column`
- `nullable = false` na entidade sem refletir o banco
- nome de tabela inferido incorretamente pelo Hibernate

Mitigação:

- explicitar `@Table` e `@Column` nos campos relevantes
- revisar SQL e Java lado a lado

## 2. Relacionamentos bidirecionais causando serialização recursiva

Se essas entidades forem expostas diretamente em controllers no futuro, pode haver recursão infinita em JSON.

Mitigação:

- nesta sprint, manter foco em persistência
- evitar usar entidades como DTO de API

## 3. Uso indevido de `CascadeType.ALL`

Pode causar persistência ou remoção em cascata sem intenção clara.

Mitigação:

- começar sem cascade, ou com o mínimo necessário

## 4. Uso de `FetchType.EAGER`

Isso tende a piorar performance e criar consultas desnecessárias.

Mitigação:

- preferir `LAZY` nas associações `@ManyToOne`

## 5. Incerteza sobre campos de negócio

Como as tasks não definem detalhadamente o domínio de `UserContext` e `Project`, existe risco de criar campos insuficientes ou excessivos.

Mitigação:

- começar com modelo mínimo e extensível
- evitar enumerações e constraints de negócio muito rígidas sem evidência funcional

## Estratégia de validação

Após implementação, a validação recomendada é:

1. Compilar o módulo com Maven.
2. Subir o `auth-service` com banco acessível.
3. Confirmar execução das migrations pelo Flyway.
4. Confirmar que o contexto Spring inicia sem erro JPA.
5. Se possível, adicionar ao menos um teste simples de carregamento de contexto.

## Definição de pronto

As tasks podem ser consideradas concluídas quando:

- existirem `V2__create_user_contexts_table.sql` e `V3__create_projects_table.sql`
- as entidades `User`, `UserContext` e `Project` existirem e compilarem
- os repositories `UserRepository`, `UserContextRepository` e `ProjectRepository` existirem e compilarem
- o projeto subir sem erro de Flyway ou JPA
- a documentação deixar de divergir do estado real do código

## Recomendação final

Para esta sprint, a melhor abordagem é entregar uma primeira versão enxuta, consistente e fácil de evoluir:

- `User` como agregado principal
- `UserContext` e `Project` ligados diretamente a `User`
- migrations simples e explícitas
- entidades JPA sem excesso de abstração
- repositories apenas com operações essenciais

Essa estratégia equilibra velocidade de entrega, clareza arquitetural e segurança para os próximos incrementos do `auth-service`.
