# Portability Platform

Plataforma fullstack que simula a **portabilidade de convênios de desconto em folha**: uma empresa passa
a ser a nova credora de parcelas hoje devidas a outro credor. A integração com a **Central de Convênios**
simulada cobre `opt-in`, agenda de descontos, registro da operação, confirmação assíncrona e trilha de
auditoria.

> **Estado atual:** estrutura inicial (scaffold) concluída. Camadas, ports, DTOs, entidades JPA e
> placeholders de UI prontos; **nenhuma regra de negócio está implementada**. Casos de uso e adapters
> lançam `UnsupportedOperationException`, os endpoints da Central retornam `501` e apenas
> `GET /api/health` responde efetivamente. Status por funcionalidade:
> [`docs/roadmap.md`](docs/roadmap.md).

Regras para agentes de IA: [`AGENTS.md`](AGENTS.md).

---

## Documentos

A lista de documentos, o dono de cada informação e a regra de uma única fonte de verdade estão em
[`AGENTS.md`](AGENTS.md), seções "Documentos" e "Onde cada informação mora". Comandos e práticas de cada
projeto: [`backend/AGENTS.md`](backend/AGENTS.md) e [`frontend/AGENTS.md`](frontend/AGENTS.md).

---

## Tecnologias

Versões e justificativas das escolhas abaixo; novas dependências exigem justificativa e registro nesta
seção.

| Camada | Tecnologia | Versão | Justificativa |
| --- | --- | --- | --- |
| Linguagem / runtime | Java | 21 | Requisito do usuário; compatível com Spring Boot 4 |
| Framework backend | Spring Boot (Web, Data JPA, Validation) | 4.2.0 | Requisito do usuário; ecossistema maduro para REST + JPA |
| Build | Maven | 3.6.3+ | Exigido pelo Spring Boot 4; já disponível no ambiente |
| Banco | H2 (em memória) | gerenciado pelo Boot | Execução sem infraestrutura externa; ideal para o scaffold |
| ORM | Hibernate / Spring Data JPA | gerenciado pelo Boot | Mapeamento dos ports de repositório com baixo código |
| Frontend | React + TypeScript | 19 / 5.9 | Requisito do desafio; TS recomendado no PDF |
| Build frontend | Vite + plugin-react | 6.4 / 4.7 | Dev server rápido e proxy `/api` nativo |
| Testes backend | JUnit 5 + Spring Boot Test | gerenciado pelo Boot | Smoke test de contexto já incluído |
| Testes frontend | Vitest + Testing Library + jsdom | 3.2 / 16.3 / 26.1 | Testes de comportamento da UI em ambiente DOM |
| Estilos | CSS puro | — | Frontend simplificado; sem dependência de UI kit |
| Documentação | Markdown + Mermaid | — | Diagramas versionáveis, renderizados pelo GitHub |
| Spec | `pdftotext` (poppler) | opcional | Utilizado para ler o PDF do desafio |

**Não incluído de propósito:** Lombok, Flyway/Liquibase, React Router, biblioteca de estado global,
biblioteca de componentes, Docker/CI. Qualquer inclusão futura exige justificativa em
[Decisões técnicas](#decisões-técnicas).

---

## Arquitetura

Visão geral e fluxos: [`docs/hld.md`](docs/hld.md). Detalhes de ports, mapeamentos e persistência:
[`docs/lld.md`](docs/lld.md). Definição, convenções, restrições e estrutura de diretórios:
[`AGENTS.md`](AGENTS.md), seções "Arquitetura definida", "Restrições arquiteturais" e "Estrutura de
diretórios".

---

## Execução

Execute Maven e npm diretamente na máquina: o Java padrão do ambiente é 17, portanto exporte
`JAVA_HOME` para o JDK 21 antes de qualquer comando Maven. Práticas específicas em
[`backend/AGENTS.md`](backend/AGENTS.md) e [`frontend/AGENTS.md`](frontend/AGENTS.md).

### Pré-requisitos

| Ferramenta | Versão mínima/prevista | Verificação |
| --- | --- | --- |
| JDK 21 | 21.0.3-tem (SDKMAN) | `java -version` com `JAVA_HOME` ajustado |
| Maven | 3.6.3 | `mvn -version` |
| Node.js | 20.11.1 | `node --version` |
| npm | 10.2.4 | `npm --version` |
| `pdftotext` (opcional) | poppler-utils | `pdftotext -v` — apenas para reler a spec |

### Configuração do ambiente

```bash
# 1) Selecionar o JDK 21
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.3-tem"
export PATH="$JAVA_HOME/bin:$PATH"
java -version    # deve mostrar 21.x

# 2) Instalar as dependências do frontend (uma vez)
cd frontend
npm ci
cd ..

# 3) Opcional: segredo HMAC para a integração simulada
export CENTRAL_HMAC_SECRET="dev-secret-change-me"
```

Não é necessário banco externo, variável de ambiente obrigatória nem serviço adicional: a Central roda
no próprio backend.

### Variáveis de ambiente

| Variável | Consumida por | Padrão | Descrição |
| --- | --- | --- | --- |
| `JAVA_HOME` | Maven / build | `21.0.3-tem` (precisa ser exportada) | Seleciona o JDK 21 |
| `CENTRAL_HMAC_SECRET` | Backend | `dev-secret-change-me` | Segredo usado por `HmacSignatureService` (via `application.yml`) |
| `CENTRAL_BASE_URL` | Backend | `http://localhost:8080/central` | Relaxed binding de `central.base-url`; também configurável por `--central.base-url=...` |
| `SERVER_PORT` | Backend | `8080` | Sobrescreve `server.port`; ao alterar, ajuste o proxy em `vite.config.ts` |
| `VITE_API_URL` | Frontend | `/api` | Base URL usada por `api/client.ts`; em dev, o proxy Vite encaminha `/api` para `:8080` |

Não há arquivo `.env` versionado. Em desenvolvimento, `VITE_API_URL` pode ser definida em `.env.local`
(ignorado pelo Git, se criado).

### Backend

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.3-tem"
cd backend
mvn spring-boot:run
```

- API: `http://localhost:8080`
- Health check: `http://localhost:8080/api/health`
- Central simulada: `http://localhost:8080/central`
- Console H2: `http://localhost:8080/h2-console` — JDBC URL `jdbc:h2:mem:portability`, usuário `sa`,
  senha vazia.

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

O Hibernate cria as tabelas no startup (`spring.jpa.hibernate.ddl-auto: update`) em H2 memória
(`jdbc:h2:mem:portability;DB_CLOSE_DELAY=-1`). Ainda não há seed, índices ou constraints de unicidade —
idempotência não foi implementada. Mapeamentos de domínio em
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

## Decisões técnicas

| Decisão | Escolha | Motivo / trade-off |
| --- | --- | --- |
| Backend | Spring Boot 4.2.0 + Java 21 | Requisito do usuário; JDK 21 disponível via SDKMAN |
| Estrutura | Hexagonal (`domain` + `application` + `adapters/in` + `adapters/out`) | Isola regras de framework, REST e banco; custo de mais arquivos e mapeamentos |
| Persistência | H2 em memória com JPA | Zero config para o scaffold; dados não sobrevivem a restart |
| Central | Mock REST no próprio backend (`/central`) | Mantém apenas `backend/` e `frontend/` e um único comando de execução; acopla deploy da simulação ao da API |
| Frontend | Vite 6 + React 19 + TypeScript 5.9, CSS puro, sem router | Pedido de "forma simplificada"; se o fluxo crescer, avaliar React Router e TanStack Query |
| Idioma | Código/contrato `/api` em inglês; contrato da Central em pt-BR | Nomes de pastas em inglês pedidos pelo usuário; PDF usa pt-BR na Central |
| Stubs | `UnsupportedOperationException` / `501` | Deixa explícito o que não existe, evitando dados falsos que pareçam prontos |
| Testes | 1 smoke test por módulo | Valida o scaffold; cenários críticos entram junto com as regras de negócio |
| Documentação | Um dono por informação; READMEs como porta de entrada com ponteiros | Evita conteúdo duplicado entre README, `docs/` e `AGENTS.md`; custo de mais links a manter |
| Versionamento | Repositório Git existente; sem histórico reescrito | Criado após o scaffold; nenhuma decisão de remote/publicação tomada nesta etapa |

---

## Limitações conhecidas

- Nenhuma regra de negócio está implementada: use cases, repositórios, cliente da Central e assinatura
  são stubs; os endpoints de negócio da API retornam `404` e os da Central, `501`.
- A UI apenas exibe os quatro painéis; não há formulários, chamadas à API nem exibição de erros.
- H2 é volátil e não há migrações de schema; `ddl-auto: update` é adequado apenas ao scaffold.
- Não há autenticação/autorização, nem validação real de entrada, nem `@ControllerAdvice` para erros.
- Idempotência, HMAC efetivo, retry/backoff, timeouts e auditoria append-only ainda não existem.
- A propriedade `central.mock.enabled` está declarada, mas ainda não é usada para ligar/desligar o mock.
- Não há CI/CD, Docker, seed de dados, paginação, versionamento de API nem testes de contrato.
- A central "real" é uma simulação local; o contrato do PDF é um ponto de partida, não uma spec fechada.
- O repositório ainda não foi publicado (a entrega final do PDF pede um link público).

---

> **Manutenção:** sempre que o projeto evoluir, atualize neste mesmo commit o que é deste README:
> execução, API, modelo de dados, tecnologias, decisões e limitações.
