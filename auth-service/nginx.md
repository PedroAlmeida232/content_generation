# Configuração do NGINX para o Auth Service

Para garantir que o `auth-service` processe corretamente os cabeçalhos encaminhados pelo NGINX (como `X-Forwarded-For`, `X-Forwarded-Proto`, etc.), é necessário configurar a estratégia de repasse de cabeçalhos no Spring Boot.

Escolha o formato do arquivo de configuração que você está utilizando e adicione as linhas abaixo:

### Opção 1: `application.properties`

Adicione a seguinte configuração no seu arquivo:

```properties
server.forward-headers-strategy=framework
```

---

### Opção 2: `application.yml`

Se você estiver utilizando YAML, adicione o seguinte bloco:

```yaml
server:
  forward-headers-strategy: framework
```
