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
| `Authorization` | `authorizationId`, `beneficiaryId`, `agreementId`, `createdAt` |
| `AuthorizationResult` | `authorization`, `created` |
| `PortabilityOperationResult` | `operation`, `created` |
| `DiscountInstallment` | `installmentId`, `agreementId`, `amount` (`BigDecimal`), `discountDate` (`LocalDate`), `status` |
| `ReceivingAccount` | `bank`, `agency`, `account` |
| `PortabilityOperation` | `operationId`, `beneficiaryId`, `installmentIds`, `creditorAssignee`, `receivingAccount`, `status`, `protocol`, `rejectionReason`, `requestedAt`, `version` |
| `AuditEvent` | `eventId`, `operationId`, `eventType` (`String`), `message`, `occurredAt` |
| `CentralOperationStatus` | `status`, `rejectionReason`, `retryAfter` (`Duration`) |

`Validation` é a dona das invariantes de entrada; os records chamam-no no construtor e os DTOs de entrada
da web chamam `validate()` antes de invocar o caso de uso. `PortabilityOperation.transitionTo` só permite
`PENDING -> CONFIRMED/REJECTED` e guarda o motivo da rejeição; `withReceivingAccount` exige `CONFIRMED`.
Ambos devolvem novo record com a mesma `version`.

Enums: `OperationStatus` (`PENDING`, `CONFIRMED`, `REJECTED`), `InstallmentStatus` (`OPEN`, `PORTED`,
`CANCELLED`) e `AuditEventType` (`REQUESTED`, `CONFIRMED`, `REJECTED`, `INTEGRATION_FAILURE`,
`ACCOUNT_UPDATED`). Cada constante de `AuditEventType` expõe `externalValue()` com o valor gravado e
devolvido no contrato (`SOLICITADA`, `CONFIRMADA`, `REJEITADA`, `FALHA_INTEGRACAO`, `CONTA_ATUALIZADA`);
`AuditEvent.eventType` permanece `String` para preservar a coluna e o contrato.

Erros tipados em `domain/model` e `domain/ports/out`: `InvalidRequestException` (com `fields` por
invariante), `BeneficiaryNotAuthorizedException`, `OperationNotConfirmedException`,
`ConcurrentOperationException`, `CentralUnavailableException`; todos derivam de `DomainException`.

## Ports

### Entrada (`domain/ports/in`)

| Port | Método |
| --- | --- |
| `RegisterAuthorizationUseCase` | `registerAuthorization(beneficiaryId, agreementId): AuthorizationResult` |
| `GetDiscountAgendaUseCase` | `getAgenda(beneficiaryId): List<DiscountInstallment>` |
| `StartPortabilityUseCase` | `startPortability(beneficiaryId, installmentIds, creditorAssignee, receivingAccount): PortabilityOperationResult` |
| `GetOperationStatusUseCase` | `getOperationStatus(operationId): Optional<PortabilityOperation>` (acompanha a Central quando `PENDING`) |
| `UpdateReceivingAccountUseCase` | `updateReceivingAccount(operationId, receivingAccount): Optional<PortabilityOperation>` |
| `GetOperationAuditUseCase` | `getAuditTrail(operationId): Optional<List<AuditEvent>>` (vazio quando a operação não existe) |

### Saída (`domain/ports/out`)

| Port | Implementado por | Finalidade |
| --- | --- | --- |
| `AuthorizationRepository` | `AuthorizationRepositoryAdapter` | salvar/buscar autorização por beneficiário e convênio; verificar existência por beneficiário |
| `CentralAuthorizationPort` | `CentralClient` | registrar o opt-in na Central e devolver o `autorizacaoId` |
| `OperationRepository` | `OperationRepositoryAdapter` | salvar o agregado com chave de idempotência; salvar estado completo com versão esperada; buscar por id e por chave |
| `AuditEventRepository` | `AuditEventRepositoryAdapter` | anexar eventos e listar os de uma operação em ordem de ocorrência |
| `DiscountAgendaPort` | `CentralClient` | buscar agenda na Central (usada também para validar a seleção) |
| `CentralPortabilityPort` | `CentralClient` | registrar operação assinada, consultar status assíncrono e atualizar a conta de recebimento |
| `IdGenerator` | `UuidIdGenerator` / `CorrelationIdFilter` | fornecer ids de operação/eventos e correlation id |
| `TransactionPort` | `TransactionalAdapter` | demarcar unidade de trabalho sem expor Spring à aplicação |
| `MetricsPort` | `LoggingMetricsAdapter` | contar desfechos nos seams, via log (sem dependência nova) |
| `BackoffPort` | `ThreadBackoffAdapter` | aplicar a espera entre tentativas de consulta de status |

Assinatura HMAC e montagem do corpo canônico ficam em `CentralRequestCodec` (adapter), usado tanto por
`CentralClient` quanto pelo mock; não há `SignaturePort`. A aplicação é dividida em um serviço por port
de entrada (`RegisterAuthorizationService`, `GetDiscountAgendaService`, `StartPortabilityService`,
`GetOperationStatusService`, `UpdateReceivingAccountService`, `GetOperationAuditService`), sem Spring,
e a composição acontece em `adapters/in/config/ApplicationConfiguration`. `Clock`, `IdGenerator`,
`TransactionPort` e `MetricsPort` são injetados; a aplicação não chama `Instant.now`, `LocalDate.now`
nem `UUID.randomUUID`.

A configuração da Central é tipada em `CentralProperties` (`central.base-url`, `central.hmac-secret`),
com relaxed binding de `CENTRAL_BASE_URL` e `CENTRAL_HMAC_SECRET`. O schema continua gerado por Hibernate
(`ddl-auto: update`); não há ferramenta de migration.

## Mapeamento com a Central

A tradução acontece em `adapters/out/central`:

| Interno (en) | Central (pt-BR) |
| --- | --- |
| `beneficiaryId` | `beneficiarioId` |
| `agreementId` | `convenioId` |
| `authorizationId` | `autorizacaoId` |
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
- `OperationJpaEntity` usa `@ElementCollection` + `@OrderColumn` para `installmentIds` (tabela
  `operation_installments`, coluna `position`), `idempotency_key` única para reconhecer reenvios e
  `@Version` na coluna `version`.
- `AuditEventRepositoryAdapter.append` é **append-only**: insert sem update/delete.
- `OperationRepository.save(operation, idempotencyKey)` insere a operação nova.
  `save(operation)` persiste o agregado inteiro (status, motivo, conta, versão) e confere a versão lida
  pelo chamador; versão desatualizada ou ausente vira `ConcurrentOperationException` (`409`), inclusive
  quando o conflito vem do `OptimisticLockingFailureException` do JPA. Não há `findByProtocol`,
  `updateStatus` nem `updateReceivingAccount`.
- Escritas de operação e eventos acontecem na mesma unidade de trabalho (`TransactionPort`); falha ao
  anexar evento desfaz a operação. A listagem de eventos usa
  `findByOperationIdOrderByOccurredAtAsc`, sem update ou delete.
- O datasource padrão usa PostgreSQL (`jdbc:postgresql://localhost:5433/portability`) com
  `ddl-auto: update`; o serviço é subido por `compose.yaml` na raiz.
- O perfil `test` usa H2 em memória com `ddl-auto: create-drop`.
- O esquema físico atual está documentado no README, seção "Modelo de dados".

## Integração, idempotência e erros

- Opt-in: antes de chamar a Central, `findByBeneficiaryAndAgreement` devolve a autorização existente;
  o reenvio não cria duplicata nem repete a chamada. A autorização nova usa o `autorizacaoId` devolvido
  pela Central e `createdAt` gerado na aplicação.
- Agenda: `getAgenda` exige ao menos uma autorização do beneficiário (`existsByBeneficiaryId`); sem ela,
  `BeneficiaryNotAuthorizedException` vira `403` e a Central não é chamada. `CentralClient.fetchAgenda`
  traduz `parcelaId`/`convenioId`/`valor`/`dataDesconto`/`status` e devolve a lista completa, sem filtro
  de elegibilidade; status HTTP diferente de `200`, corpo ausente/inválido, data fora de `AAAA-MM-DD` ou
  status desconhecido viram `CentralUnavailableException` (`502`). O `GET` da agenda não envia corpo nem
  assinatura, e a fixture da Central é fixa por beneficiário.
- `CentralClient`: chamadas HTTP com timeout e assinatura HMAC enviada no header `X-Signature`. Em todas
  as chamadas com corpo, `CentralRequestCodec` assina o corpo canônico em pt-BR sem o campo `assinatura` e
  envia o HMAC também nesse campo. Qualquer resposta diferente da esperada (`201` no opt-in, `202` no
  registro), corpo ausente/inválido ou protocolo em branco vira `CentralUnavailableException` (`502`).
- Consulta de status: `CentralClient.consultStatus` faz um `GET` sem assinatura a
  `/operacoes-portabilidade/{protocolo}`. `204` vira `PENDING` com o `Retry-After` do cabeçalho; `200`
  traduz `CONFIRMADA`/`REJEITADA` e `motivoRejeicao`; status HTTP diferente de `200`/`204`, corpo
  inválido, status desconhecido ou `Retry-After` inválido viram `CentralUnavailableException` (`502`).
- Backoff: `getOperationStatus` consulta a Central no máximo três vezes enquanto a operação estiver
  `PENDING`. A espera antes da tentativa `n` é `min(5s, max(Retry-After, 1s × 2^(n-1)))`; `BackoffPort`
  aplica a espera e a política fica no caso de uso. Esgotadas as tentativas, a operação permanece
  `PENDING` e o `GET` responde `200` sem evento novo. A confirmação continua no caminho do `GET`
  (decisão de manter polling); uma interrupção durante o backoff restaura a flag do thread e devolve a
  operação como estava.
- Mock de status: o desfecho é escolhido no registro pelo valor de `credorCessionario` (case-insensitive) e
  guardado por protocolo em mapa em memória. Sem marcador ou com `REJEITAR`, as duas primeiras consultas
  respondem `204` com `Retry-After: 1`; a partir da terceira, `CONFIRMADA` ou `REJEITADA` com motivo,
  respectivamente. Com `FALHA`, toda consulta responde `503` sem corpo, sem contador e sem transição.
  `REJEITAR` prevalece se ambos os marcadores aparecerem. Protocolo desconhecido responde `404`. A transição
  final usa `save(operation)` com a versão esperada e anexa `CONFIRMADA` ou `REJEITADA` com o protocolo
  e, quando houver, o motivo; um evento terminal já registrado não é duplicado. Operação já finalizada
  não consulta a Central.
- A Central valida as duas assinaturas: `201` com `autorizacaoId` no opt-in e `202` com `protocolo` no
  registro; `400` para payload inválido e `401` para assinatura ausente/inválida. O protocolo do mock é
  determinístico por payload. Na API, falha de integração vira `502`.
- `HmacSignatureService`: HMAC-SHA256 em Base64 com segredo de `central.hmac-secret`; verificação em
  tempo constante.
- Registro: antes de qualquer chamada à Central, `startPortability` calcula a chave SHA-256 em hex de
  `beneficiaryId|parcelas ordenadas|credor|banco|agência|conta` e consulta `findByIdempotencyKey`; se
  existir, devolve a operação com `created = false` (`200`). Caso contrário, exige autorização do
  beneficiário (`403`), reconsulta a agenda e só aceita parcelas existentes, `OPEN` e com data futura,
  sem duplicatas. A criação persiste a operação `PENDING` e anexa um evento `SOLICITADA` com o protocolo;
  falha da Central não grava operação nem evento.
- Erros: `ApiExceptionHandler` traduz `InvalidRequestException` em `400` (com `fields` por invariante),
  `BeneficiaryNotAuthorizedException` em `403`, operação inexistente em `404`,
  `OperationNotConfirmedException`/`ConcurrentOperationException` em `409` e
  `CentralUnavailableException` em `502`. Toda resposta de erro traz `message` e, quando disponível,
  `correlationId`; o header `X-Correlation-Id` é propagado por `CorrelationIdFilter` e registrado no MDC.
  Respostas de sucesso usam DTOs explícitos, sem a `version` do agregado. Quando a operação já existe, a
  aplicação anexa `FALHA_INTEGRACAO` com o ID da operação e o contexto da chamada ao redor de
  `consultStatus` e `updateReceivingAccount`, propaga a exceção e não altera status, motivo nem conta.
  Falhas no opt-in, na agenda ou no registro não geram evento órfão.
- Transições de `OperationStatus` (`CONFIRMED`/`REJECTED`) e seus eventos são F04. A conta de recebimento
  pode ser corrigida somente em operações `CONFIRMED` (F05): a aplicação envia `PUT` assinado à Central com
  `protocolo`, `banco`, `agencia` e `conta`, persiste a nova conta e anexa `CONTA_ATUALIZADA` somente quando
  os dados mudam; conta igual não chama a Central nem gera evento, e falha da Central não altera o estado
  local. A trilha é exposta por `GET /api/portability/{operationId}/audit`: `200` com os eventos ordenados
  por ocorrência (ou `[]`), `404` se a operação não existir, sem chamar a Central nem gravar eventos.

## Estratégia de testes

Cenários obrigatórios e comandos estão no [`../AGENTS.md`](../AGENTS.md), seções "Diretrizes para
testes" e "Verificação completa". Os testes de aplicação ficam em `application/usecase`, um por caso de
uso, com os fakes compartilhados em `TestDoubles`: idempotência, elegibilidade, transições, eventos,
falhas da Central, backoff e interrupção, versão desatualizada e rollback. `OperationTransactionalTest` e
`OperationConfirmationConcurrencyTest` exercitam atomicidade e concorrência com H2. Adapters usam testes
em processo para a Central e `MockMvc` para contratos HTTP; a UI é verificada com Vitest + Testing
Library. A suíte de auditoria não fixa ordem entre eventos com o mesmo `occurredAt`.
