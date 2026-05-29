# Guia da Equipe — Sistema de Monitoria UCSAL
## Como criar e subir um microserviço nesta arquitetura

> Leia do início ao fim antes de escrever qualquer código.

---

## Visão geral da arquitetura

```
[Angular :4200]
       │
       ▼
[API Gateway :8080]          ← valida JWT, roteia, injeta headers
       │
       ├──► [ms-auth      :8081]   sem JWT (rota pública)
       ├──► [ms-escola    :8082]   com JWT
       ├──► [ms-professor :8083]   com JWT
       ├──► [ms-monitoria :8084]   com JWT
       └──► [ms-relatorio :8086]   com JWT
                  │
         todos registrados em
  [Service Discovery (Eureka) :8761]
```

O **Service Discovery** (Eureka) é o catálogo central. Todo microserviço se registra nele ao subir. O **API Gateway** consulta esse catálogo e sabe para onde encaminhar cada requisição — você não precisa configurar IP nem porta de nenhum serviço manualmente.

---

## 1. Ordem obrigatória para subir os serviços

```
1º  service-discovery   → sempre primeiro, os outros dependem dele
2º  seu microserviço    → qualquer ordem entre si
3º  api-gateway         → sempre por último
```

Se o Gateway subir antes do Eureka, ele vai falhar na inicialização. Se o seu microsserviço subir antes do Eureka, ele vai tentar se registrar e falhar nas tentativas iniciais — não é erro fatal, ele tenta novamente automaticamente, mas é melhor já ter o Eureka no ar.

---

## 2. Como criar seu microserviço — passo a passo

### 2.1 Use o ms-teste como template

O projeto `ms-teste` já está configurado e funcionando nesta arquitetura. **Copie ele** e adapte. Não crie do zero.

```
ms-teste/
├── pom.xml
└── src/main/
    ├── java/br/edu/ucsal/mteste/
    │   ├── MsTesteApplication.java
    │   └── controller/
    │       └── TesteController.java
    └── resources/
        └── application.yml
```

### 2.2 Renomeie os 3 pontos obrigatórios

Suponha que você vai criar o `ms-professor`. Você precisa mudar exatamente três coisas:

**`pom.xml`** — artifactId e name:
```xml
<artifactId>ms-professor</artifactId>
<name>ms-professor</name>
```

**`application.yml`** — nome da aplicação e porta:
```yaml
server:
  port: 8083          # ← porta exclusiva (veja tabela de portas abaixo)

spring:
  application:
    name: ms-professor  # ← CRÍTICO: deve ser EXATAMENTE igual ao que está no Gateway
```

**Classe principal** — pacote e nome da classe:
```java
package br.edu.ucsal.mprofessor;   // mude o pacote

@SpringBootApplication
@EnableDiscoveryClient             // mantenha esta anotação
public class MsProfessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsProfessorApplication.class, args);
    }
}
```

### 2.3 application.yml completo para qualquer microsserviço

```yaml
server:
  port: XXXX   # sua porta (tabela abaixo)

spring:
  application:
    name: ms-seu-nome   # mesmo nome que está no Gateway

  datasource:           # só se usar banco de dados
    url: jdbc:postgresql://localhost:5432/seu_banco
    username: postgres
    password: sua_senha
    driver-class-name: org.postgresql.Driver

  jpa:                  # só se usar JPA
    hibernate:
      ddl-auto: update
    show-sql: true

eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true

logging:
  level:
    root: INFO
```

---

## 3. Tabela de portas e nomes (não altere)

| Microserviço     | `spring.application.name` | Porta | Responsável |
|-----------------|--------------------------|-------|-------------|
| ms-auth         | `ms-auth`                | 8081  |             |
| ms-escola       | `ms-escola`              | 8082  |             |
| ms-professor    | `ms-professor`           | 8083  |             |
| ms-monitoria    | `ms-monitoria`           | 8084  |             |
| ms-relatorio    | `ms-relatorio`           | 8086  |             |

> ⚠️ O `spring.application.name` precisa ser **idêntico** ao nome configurado nas rotas do Gateway (`lb://ms-professor`, `lb://ms-escola` etc.). Qualquer diferença e o Gateway não encontra o serviço.

---

## 4. Como o Gateway roteia para o seu serviço

O `application.yml` do Gateway já tem as rotas configuradas. Exemplo para ms-professor:

```yaml
- id: ms-professor
  uri: lb://ms-professor          # lb:// = load balancer via Eureka
  predicates:
    - Path=/api/professores/**    # toda requisição nesse caminho vai para este serviço
  filters:
    - JwtAuthFilter               # valida o token JWT antes de encaminhar
```

Isso significa que quando o Angular chama `GET http://localhost:8080/api/professores`, o Gateway intercepta, valida o token, e repassa para `http://localhost:8083/api/professores` automaticamente. **Você não precisa configurar nada além do nome e da porta.**

Se o seu serviço tiver endpoints em paths diferentes, avise quem fez o Gateway para adicionar a rota.

---

## 5. Autenticação — como funciona na prática

### Fluxo completo:

```
1. Angular faz POST /api/auth/login  →  Gateway  →  ms-auth
2. ms-auth valida credenciais e retorna { token: "eyJ..." }
3. Angular salva o token
4. Nas próximas requisições, Angular envia:
   Authorization: Bearer eyJ...
5. Gateway valida o token e injeta dois headers internos:
   X-User-Name: joao.silva
   X-User-Role: PROFESSOR
6. Seu microserviço recebe a requisição com esses headers
```

### Como capturar os headers no seu Controller:

```java
@GetMapping("/meus-dados")
public ResponseEntity<?> meusDados(
        @RequestHeader("X-User-Name") String username,
        @RequestHeader("X-User-Role") String role) {

    // use para filtrar dados por usuário logado
    // ex: buscar só os professores com login == username
}
```

### Rota pública (sem autenticação)

O endpoint `/api/auth/**` não exige token — é por ele que o login acontece. Se o seu serviço tiver algum endpoint público (improvável, mas possível), avise quem fez o Gateway para remover o `JwtAuthFilter` da rota.

---

## 6. O JWT — o que o ms-auth precisa gerar

O token gerado pelo ms-auth **deve usar a mesma chave secreta** configurada no Gateway. A chave padrão está em `application.yml` do Gateway:

```yaml
app:
  jwt:
    secret: dXNhX3VtYV9jaGF2ZV9zZWNyZXRhX211aXRvX2xvbmdhX2Jhc2U2NA==
```

O token deve conter os campos `sub` (username) e `role` (ou `roles`) no payload:

```json
{
  "sub": "joao.silva",
  "role": "PROFESSOR",
  "iat": 1234567890,
  "exp": 1234654290
}
```

**Dependências no `pom.xml` do ms-auth para gerar JWT:**
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
```

---

## 7. Dependências mínimas no pom.xml de cada microserviço

```xml
<properties>
  <java.version>17</java.version>
  <spring-cloud.version>2023.0.1</spring-cloud.version>
</properties>

<dependencies>
  <!-- Web -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>

  <!-- Eureka Client — obrigatório para se registrar -->
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
  </dependency>

  <!-- Banco de dados (adicione se usar JPA + PostgreSQL) -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
  </dependency>
</dependencies>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>${spring-cloud.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

---

## 8. Verificando se tudo está funcionando

### Checar se o serviço está registrado no Eureka
Acesse http://localhost:8761 — o nome do seu serviço deve aparecer em **"Instances currently registered with Eureka"** com status `UP`.

### Testar via Gateway (use Postman/Insomnia)

**Sem autenticação (ms-auth ou ms-teste):**
```
GET http://localhost:8080/api/teste/ping
```

**Com autenticação:**
```
POST http://localhost:8080/api/auth/login
Body: { "login": "admin", "senha": "123456" }

→ copie o token retornado, depois:

GET http://localhost:8080/api/professores
Headers: Authorization: Bearer <token>
```

### Erros comuns e como resolver

| Erro | Causa | Solução |
|------|-------|---------|
| `503 Service Unavailable` | Serviço não registrado no Eureka | Confirme que o serviço subiu e está em http://localhost:8761 |
| `401 Unauthorized` | Token ausente ou inválido | Inclua o header `Authorization: Bearer <token>` |
| `Connection refused` no startup | Eureka não está no ar | Suba o `service-discovery` primeiro |
| Serviço não aparece no Eureka | `spring.application.name` errado ou `defaultZone` errado | Confira o `application.yml` do seu serviço |
| `404 Not Found` no Gateway | Rota não cadastrada no Gateway | Verifique se o path bate com o configurado no `application.yml` do Gateway |

---

## 9. Checklist antes de apresentar

- [ ] `service-discovery` sobe sem erro na porta 8761
- [ ] Seu microserviço aparece listado em http://localhost:8761 com status `UP`
- [ ] `api-gateway` sobe sem erro na porta 8080
- [ ] Chamada via Gateway chega no seu controller (teste com Postman)
- [ ] Endpoints protegidos retornam `401` sem token
- [ ] Endpoints protegidos funcionam com token válido
- [ ] Headers `X-User-Name` e `X-User-Role` chegam no controller (logue e verifique)
- [ ] Repositório no github/ucsal com histórico de commits **exclusivamente seu**
