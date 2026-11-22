# LLD — Low-level design

> **Dono:** detalhes de implementação (ports, mapeamentos, persistência, integração, erros e testes).
> Visão geral em [`hld.md`](hld.md), contratos HTTP no README (seção "Documentação da API"), esquema
> físico no README (seção "Modelo de dados"), convenções no [`../AGENTS.md`](../AGENTS.md) e termos no
> [`../GLOSSARY.md`](../GLOSSARY.md). Quando uma tarefa alterar algo registrado aqui, carregue a skill
> `lld` (se disponível) em modo atualizar antes de editar este arquivo.

## Modelo de domínio

Records imutáveis em `domain/model`, todos sem anotações de framework:

| Tipo | Campos |
| --- | --- |
| `Beneficiary` | `beneficiaryId`, `name` |
| `Authorization` | `authorizationId`, `beneficiaryId`, `agreementId`, `createdAt` |
| `DiscountInstallment` | `installmentId`, `agreementId`, `amount` (`BigDecimal`), `discountDate` (`LocalDate`), `status` |
| `ReceivingAccount` | `bank`, `agency`, `account` |
| `PortabilityOperation` | `operationId`, `beneficiaryId`, `installmentIds`, `creditorAssignee`, `receivingAccount`, `status`, `protocol`, `rejectionReason`, `requestedAt` |
| `AuditEvent` | `eventId`, `operationId`, `eventType`, `message`, `occurredAt` |
| `CentralOperationStatus` | `status`, `rejectionReason` |

Enums: `OperationStatus` (`PENDING`, `CONFIRMED`, `REJECTED`), `InstallmentStatus` (`OPEN`, `PORTED`,
`CANCELLED`).

## Ports

### Entrada (`domain/ports/in`)

| Port | Método |
| --- | --- |
| `RegisterAuthorizationUseCase` | `registerAuthorization(beneficiaryId, agreementId): Authorization` |
| `GetDiscountAgendaUseCase` | `getAgenda(beneficiaryId): List<DiscountInstallment>` |
| `StartPortabilityUseCase` | `startPortability(beneficiaryId, installmentIds, creditorAssignee, receivingAccount): PortabilityOperation` |
| `GetOperationStatusUseCase` | `getOperationStatus(operationId): Optional<PortabilityOperation>` |
| `GetOperationAuditUseCase` | `getAuditTrail(operationId): List<AuditEvent>` |

### Saída (`domain/ports/out`)

| Port | Implementado por | Finalidade |
| --- | --- | --- |
| `AuthorizationRepository` | `AuthorizationRepositoryAdapter` | salvar/buscar autorização por beneficiário e convênio |
| `OperationRepository` | `OperationRepositoryAdapter` | salvar/buscar operação por id e por protocolo (idempotência) |
| `AuditEventRepository` | `AuditEventRepositoryAdapter` | anexar eventos e listar por operação |
| `DiscountAgendaPort` | `CentralClient` | buscar agenda na Central |
| `CentralPortabilityPort` | `CentralClient` | registrar operação e consultar status por protocolo |
| `SignaturePort` | `HmacSignatureService` | assinar e verificar mensagens |

`PortabilityApplicationService` implementa os cinco ports de entrada e depende apenas dos ports de saída.
Hoje todos os métodos lançam `UnsupportedOperationException`.

## Mapeamento com a Central

A tradução acontece em `adapters/out/central`:

| Interno (en) | Central (pt-BR) |
| --- | --- |
| `beneficiaryId` | `beneficiarioId` |
| `agreementId` | `convenioId` |
| `installmentId` | `parcelaId` |
| `amount` | `valor` |
| `discountDate` | `dataDesconto` |
| `OPEN` / `PORTED` / `CANCELLED` | `ABERTA` / `PORTADA` / `CANCELADA` |
| `ReceivingAccount(bank, agency, account)` | `contaRecebimento(banco, agencia, conta)` |
| `creditorAssignee` | `credorCessionario` |
| `signature` | `assinatura` |
| `protocol` | `protocolo` |
| `PENDING` / `CONFIRMED` / `REJECTED` | `PENDENTE` / `CONFIRMADA` / `REJEITADA` |
| `rejectionReason` | `motivoRejeicao` |

## Persistência

- Entidades JPA em `adapters/out/persistence` são **espelho** do domínio; nunca vazam para ports ou
  respostas HTTP.
- `OperationJpaEntity` usa `@ElementCollection` para `installmentIds` (tabela `operation_installments`).
- `AuditEventRepositoryAdapter.append` deve ser **append-only**: insert sem update/delete.
- `findByProtocol` existe para suportar idempotência; a constraint de unicidade ainda será decidida junto
  com a implementação (ver `docs/roadmap.md`, F03).
- O datasource padrão usa PostgreSQL (`jdbc:postgresql://localhost:5433/portability`) com
  `ddl-auto: update`; o serviço é subido por `compose.yaml` na raiz, que publica a porta `5433` no host
  para não colidir com uma instância local em `5432`.
- O perfil `test` usa H2 em memória com `ddl-auto: create-drop`, para `mvn test` não depender de Docker.
- O esquema físico atual está documentado no README, seção "Modelo de dados".

## Integração, idempotência e erros (a implementar)

- `CentralClient`: chamadas HTTP com timeout, assinatura HMAC enviada no payload/header e leitura de
  `Retry-After`; protocolo `202` e consulta de status com backoff.
- `HmacSignatureService`: HMAC-SHA256 em Base64 com segredo de `central.hmac-secret`; verificação em
  tempo constante.
- Idempotência: gerar chave estável a partir de beneficiário + convênio + parcelas + conta; antes de
  registrar, consultar `findByProtocol`/chave e devolver a operação existente.
- Erros: falha de integração, timeout, resposta inesperada e rejeição devem gerar evento de auditoria e
  ser traduzidos pelo adapter REST em respostas HTTP claras, exibidas no frontend.
- Transições inválidas de `OperationStatus` devem ser rejeitadas no domínio.

## Estratégia de testes

Cenários obrigatórios e comandos estão no [`../AGENTS.md`](../AGENTS.md), seções "Diretrizes para
testes" e "Verificação completa". Resumo: unitários de domínio/aplicação com fakes dos ports, `MockMvc`
para contratos HTTP, Vitest + Testing Library para comportamento visível da UI. Hoje há 1 smoke test de
contexto e 10 testes da UI (autorização, agenda, elegibilidade, registro, rejeição, auditoria e estados
de erro/vazio), com `apiFetch` mockado e `afterEach(cleanup)` porque o Vitest roda sem `globals`.

## Pendências

Todos os pontos marcados como "a implementar" acima. O estado por funcionalidade está em
[`roadmap.md`](roadmap.md).
