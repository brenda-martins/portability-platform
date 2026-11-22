# Portability Platform

Plataforma fullstack que simula a **portabilidade de convênios de desconto em folha**: uma empresa passa
a ser a nova credora de parcelas hoje devidas a outro credor. A integração com a **Central de Convênios**
simulada cobre `opt-in`, agenda de descontos, registro da operação, confirmação assíncrona e trilha de
auditoria.

Funcionalidades e status: [`docs/roadmap.md`](docs/roadmap.md).

Regras para agentes de IA: [`AGENTS.md`](AGENTS.md).

---

## Documentos

A lista de documentos, o dono de cada informação e a regra de uma única fonte de verdade estão em
[`AGENTS.md`](AGENTS.md), seções "Documentos" e "Onde cada informação mora". Comandos e práticas de cada
projeto: [`backend/AGENTS.md`](backend/AGENTS.md) e [`frontend/AGENTS.md`](frontend/AGENTS.md).

---

## Tecnologias

| Camada | Tecnologia | Versão |
| --- | --- | --- |
| Linguagem / runtime | Java | 21 |
| Framework backend | Spring Boot (Web, Data JPA, Validation) | 4.2.0 |
| Build | Maven | 3.6.3+ |
| Banco | PostgreSQL (Docker Compose) + H2 | 16-alpine / gerenciado pelo Boot |
| ORM | Hibernate / Spring Data JPA | gerenciado pelo Boot |
| Frontend | React + TypeScript | 19 / 5.9 |
| Build frontend | Vite + plugin-react | 6.4 / 4.7 |
| Testes backend | JUnit 5 + Spring Boot Test | gerenciado pelo Boot |
| Testes frontend | Vitest + Testing Library + jsdom | 3.2 / 16.3 / 26.1 |
| Estilos | CSS puro | — |
| Documentação | Markdown + Mermaid | — |

---

## Arquitetura

Visão geral e fluxos: [`docs/hld.md`](docs/hld.md). Detalhes de ports, mapeamentos e persistência:
[`docs/lld.md`](docs/lld.md). Definição, convenções, restrições e estrutura de diretórios:
[`AGENTS.md`](AGENTS.md), seções "Arquitetura definida", "Restrições arquiteturais" e "Estrutura de
diretórios".

---

## Execução

Execute Maven e npm diretamente na máquina, com Java 21 instalado. Práticas específicas em
[`backend/AGENTS.md`](backend/AGENTS.md) e [`frontend/AGENTS.md`](frontend/AGENTS.md).

### Pré-requisitos

| Ferramenta | Versão mínima/prevista | Verificação |
| --- | --- | --- |
| JDK | 21 | `java -version` |
| Maven | 3.6.3 | `mvn -version` |
| Node.js | 20.11.1 | `node --version` |
| npm | 10.2.4 | `npm --version` |
| `pdftotext` (opcional) | poppler-utils | `pdftotext -v` — apenas para reler a spec |

### Configuração do ambiente

```bash
# 1) Instalar as dependências do frontend (uma vez)
cd frontend
npm ci
cd ..

# 2) Subir o PostgreSQL (os testes usam H2 e não precisam dele)
docker compose up -d postgres

# 3) Opcional: segredo HMAC para a integração simulada
export CENTRAL_HMAC_SECRET="dev-secret-change-me"
```

A Central roda no próprio backend. Para derrubar o banco e remover o volume: `docker compose down -v`.
Sem o Compose, `mvn test` continua funcionando (perfil `test` com H2), mas `mvn spring-boot:run` não
consegue conectar ao banco.

### Variáveis de ambiente

| Variável | Consumida por | Padrão | Descrição |
| --- | --- | --- | --- |
| `DATABASE_URL` | Backend | `jdbc:postgresql://localhost:5433/portability` | URL JDBC do datasource padrão |
| `DATABASE_USER` | Backend e Compose | `portability` | Usuário do banco |
| `DATABASE_PASSWORD` | Backend e Compose | `portability` | Senha do banco |
| `DATABASE_NAME` | Compose | `portability` | Nome do banco criado no container |
| `DATABASE_PORT` | Compose | `5433` | Porta publicada no host (a 5432 costuma estar ocupada por uma instância local) |
| `CENTRAL_HMAC_SECRET` | Backend | `dev-secret-change-me` | Segredo usado por `HmacSignatureService` (via `application.yml`) |
| `CENTRAL_BASE_URL` | Backend | `http://localhost:8080/central` | Relaxed binding de `central.base-url`; também configurável por `--central.base-url=...` |
| `SERVER_PORT` | Backend | `8080` | Sobrescreve `server.port`; ao alterar, ajuste o proxy em `vite.config.ts` |
| `VITE_API_URL` | Frontend | `/api` | Base URL usada por `api/client.ts`; em dev, o proxy Vite encaminha `/api` para `:8080` |

Não há arquivo `.env` versionado. Em desenvolvimento, `VITE_API_URL` pode ser definida em `.env.local`
(ignorado pelo Git, se criado).

### Backend

Com o PostgreSQL no ar (`docker compose up -d postgres`):

```bash
cd backend
mvn spring-boot:run
```

- API: `http://localhost:8080`
- Health check: `http://localhost:8080/api/health`
- Central simulada: `http://localhost:8080/central`
- PostgreSQL: `localhost:5433`, banco/usuário/senha `portability` (padrões configuráveis pelas variáveis
  acima; dentro do container a porta é `5432`).

Para gerar o JAR: `mvn clean package` e `java -jar target/portability-backend-0.0.1-SNAPSHOT.jar`.

### Frontend

```bash
cd frontend
npm ci          # ou npm install
npm run dev     # http://localhost:5173
```

O Vite faz proxy de `/api` para `http://localhost:8080` (configurado em `vite.config.ts`). Para
produção: `npm run build` (type-check + bundle em `dist/`) e `npm run preview`.

### Testes

Comandos da verificação completa e regras para acrescentar novos:
[`AGENTS.md`](AGENTS.md), seção "Verificação completa", e os `AGENTS.md` de projeto. Critérios dos
cenários críticos: [`AGENTS.md`](AGENTS.md), seção "Diretrizes para testes".

---

## Design e interface

Linguagem visual: [`docs/design-system.md`](docs/design-system.md). Práticas de código:
[`frontend/AGENTS.md`](frontend/AGENTS.md).

---

## Planejamento

Funcionalidades, dependências, status e próximas ações: [`docs/roadmap.md`](docs/roadmap.md).
Estrutura de PRD, spec, tarefas e planos: [`docs/funcionalidades/README.md`](docs/funcionalidades/README.md).
Processo de SDD: [`AGENTS.md`](AGENTS.md), seção "Processo obrigatório de SDD".

---

## Convenções de Git

Ver [`AGENTS.md`](AGENTS.md), seção "Convenções de Git".

---

## Consulta à documentação de bibliotecas

Ver [`AGENTS.md`](AGENTS.md), seção "Consulta à documentação de bibliotecas".

---

## Agentes e skills

Ver [`AGENTS.md`](AGENTS.md), seção "Agentes e skills".

---

## Documentação da API

### API da aplicação (`/api`)

Os paths abaixo já têm controllers reservados. Como ainda não há métodos handler, **retornam `404`** —
exceto o health check.

| Método | Endpoint | Estado | Descrição prevista |
| --- | --- | --- | --- |
| `GET` | `/api/health` | ✅ `200` | Verificação de disponibilidade |
| `POST` | `/api/authorizations` | 🚧 `404` | Registrar opt-in do beneficiário |
| `GET` | `/api/beneficiaries/{beneficiaryId}/agenda` | 🚧 `404` | Listar parcelas elegíveis |
| `POST` | `/api/portability` | 🚧 `404` | Iniciar operação de portabilidade |
| `GET` | `/api/portability/{operationId}` | 🚧 `404` | Consultar status da operação |
| `GET` | `/api/portability/{operationId}/audit` | 🚧 `404` | Consultar trilha de eventos |

Exemplo de resposta implementada:

```json
{ "status": "UP", "service": "portability-backend" }
```

DTOs já definidos no frontend (`frontend/src/types/index.ts`) para o contrato planejado — nomes em inglês:

```json
// AuthorizeRequest
{ "beneficiaryId": "BEN-123", "agreementId": "AGR-001" }

// PortabilityRequest
{
  "beneficiaryId": "BEN-123",
  "installments": ["INS-1", "INS-2"],
  "creditorAssignee": "Nova Credora S.A.",
  "receivingAccount": { "bank": "001", "agency": "1234", "account": "56789-0" }
}
```

### Central de Convênios simulada (`/central`)

Contrato mínimo sugerido pelo PDF. Os quatro endpoints existem, mas retornam `501 Not Implemented`:

| Método | Endpoint | Hoje | Contrato previsto |
| --- | --- | --- | --- |
| `POST` | `/central/autorizacoes` | `501` | `201 Created` com identificador da autorização |
| `GET` | `/central/beneficiarios/{beneficiaryId}/agenda` | `501` | `200` com lista de parcelas |
| `POST` | `/central/operacoes-portabilidade` | `501` | `202 Accepted` com `{ "protocolo": "..." }` |
| `GET` | `/central/operacoes-portabilidade/{protocol}` | `501` | `200` com status ou `204` + `Retry-After` enquanto pendente |

Payloads da Central (pt-BR, conforme o PDF):

```json
// POST /central/autorizacoes
{ "beneficiarioId": "BEN-123", "convenioId": "AGR-001" }

// POST /central/operacoes-portabilidade
{
  "beneficiarioId": "BEN-123",
  "parcelas": ["INS-1", "INS-2"],
  "credorCessionario": "Nova Credora S.A.",
  "contaRecebimento": { "banco": "001", "agencia": "1234", "conta": "56789-0" },
  "assinatura": "..."
}

// GET /central/operacoes-portabilidade/{protocolo}
{ "status": "CONFIRMADA", "motivoRejeicao": null }
```

A conversão entre esses nomes e o modelo interno em inglês fica em `adapters/out/central` (detalhes em
[`docs/lld.md`](docs/lld.md), seção "Mapeamento com a Central").

### Exemplos de utilização

Verificar que o backend está no ar:

```bash
curl -i http://localhost:8080/api/health
# HTTP/1.1 200
# {"status":"UP","service":"portability-backend"}
```

Confirmar que a Central mockada está no ar (o `501` é o comportamento atual esperado):

```bash
curl -i -X POST http://localhost:8080/central/autorizacoes \
  -H 'Content-Type: application/json' \
  -d '{"beneficiarioId":"BEN-123","convenioId":"AGR-001"}'
# HTTP/1.1 501
```

Abrir a interface:

```bash
cd frontend && npm run dev
# navegue para http://localhost:5173
```

Exemplo previsto para quando os casos de uso forem implementados (ainda não funciona):

```bash
curl -X POST http://localhost:8080/api/authorizations \
  -H 'Content-Type: application/json' \
  -d '{"beneficiaryId":"BEN-123","agreementId":"AGR-001"}'
```

---

## Modelo de dados

O Hibernate cria as tabelas no startup (`spring.jpa.hibernate.ddl-auto: update`) no PostgreSQL
(`jdbc:postgresql://localhost:5433/portability`, via Compose). Nos testes, o perfil `test` usa H2 em
memória com `ddl-auto: create-drop`. Mapeamentos de domínio em
[`docs/lld.md`](docs/lld.md), seção "Persistência".

### `authorizations`

| Coluna | Tipo | Observação |
| --- | --- | --- |
| `authorization_id` | String (PK) | Identificador único da autorização |
| `beneficiary_id` | String (not null) | Beneficiário |
| `agreement_id` | String (not null) | Convênio de origem |
| `created_at` | Instant (not null) | Momento do opt-in |

### `portability_operations`

| Coluna | Tipo | Observação |
| --- | --- | --- |
| `operation_id` | String (PK) | Identificador da operação |
| `beneficiary_id` | String (not null) | Beneficiário |
| `creditor_assignee` | String | Novo credor |
| `bank` / `agency` / `account` | String | Conta de recebimento (modelo `ReceivingAccount`) |
| `status` | Enum string | `PENDING`, `CONFIRMED` ou `REJECTED` |
| `protocol` | String | Protocolo fornecido pela Central |
| `rejection_reason` | String | Preenchido quando `REJECTED` |
| `requested_at` | Instant (not null) | Momento da solicitação |

As parcelas selecionadas são armazenadas na tabela de coleção `operation_installments`
(`operation_id`, `installment_id`), mapeada por `@ElementCollection`.

### `audit_events`

| Coluna | Tipo | Observação |
| --- | --- | --- |
| `event_id` | String (PK) | Identificador do evento |
| `operation_id` | String (not null) | Operação relacionada |
| `event_type` | String (not null) | Tipo do evento (ex.: solicitação, mudança de status) |
| `message` | String | Detalhe legível, inclusive retorno da Central |
| `occurred_at` | Instant (not null) | Ordenação da trilha |

Eventos devem ser **append-only**: nunca atualizados nem apagados (regra a implementar).

---

> **Manutenção:** atualize este README somente quando mudarem execução, contratos da API, modelo de
> dados ou versões das tecnologias. Funcionalidades e status ficam em
> [`docs/roadmap.md`](docs/roadmap.md).
