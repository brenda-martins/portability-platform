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
| `AuthorizationResult` | `authorization`, `created` |
| `PortabilityOperationResult` | `operation`, `created` |
| `DiscountInstallment` | `installmentId`, `agreementId`, `amount` (`BigDecimal`), `discountDate` (`LocalDate`), `status` |
| `ReceivingAccount` | `bank`, `agency`, `account` |
| `PortabilityOperation` | `operationId`, `beneficiaryId`, `installmentIds`, `creditorAssignee`, `receivingAccount`, `status`, `protocol`, `rejectionReason`, `requestedAt` |
| `AuditEvent` | `eventId`, `operationId`, `eventType`, `message`, `occurredAt` |
| `CentralOperationStatus` | `status`, `rejectionReason`, `retryAfter` (`Duration`) |

`Authorization` e `DiscountInstallment` validam campos obrigatórios no construtor.

Enums: `OperationStatus` (`PENDING`, `CONFIRMED`, `REJECTED`), `InstallmentStatus` (`OPEN`, `PORTED`,
`CANCELLED`).

## Ports

### Entrada (`domain/ports/in`)

| Port | Método |
| --- | --- |
| `RegisterAuthorizationUseCase` | `registerAuthorization(beneficiaryId, agreementId): AuthorizationResult` |
| `GetDiscountAgendaUseCase` | `getAgenda(beneficiaryId): List<DiscountInstallment>` |
| `StartPortabilityUseCase` | `startPortability(beneficiaryId, installmentIds, creditorAssignee, receivingAccount): PortabilityOperationResult` |
| `GetOperationStatusUseCase` | `getOperationStatus(operationId): Optional<PortabilityOperation>` (acompanha a Central quando `PENDING`) |
| `GetOperationAuditUseCase` | `getAuditTrail(operationId): List<AuditEvent>` |

### Saída (`domain/ports/out`)

| Port | Implementado por | Finalidade |
| --- | --- | --- |
| `AuthorizationRepository` | `AuthorizationRepositoryAdapter` | salvar/buscar autorização por beneficiário e convênio; verificar existência por beneficiário |
| `CentralAuthorizationPort` | `CentralClient` | montar payload/HMAC, registrar o opt-in na Central e devolver o `autorizacaoId` |
| `OperationRepository` | `OperationRepositoryAdapter` | salvar, buscar por chave de idempotência, id e protocolo; atualizar status e motivo de rejeição |
| `AuditEventRepository` | `AuditEventRepositoryAdapter` | anexar eventos; listagem por operação é F06 |
| `DiscountAgendaPort` | `CentralClient` | buscar agenda na Central (usada também para validar a seleção) |
| `CentralPortabilityPort` | `CentralClient` | registrar operação assinada e consultar status assíncrono |
| `SignaturePort` | `HmacSignatureService` | assinar e verificar mensagens |
| `BackoffPort` | `ThreadBackoffAdapter` | aplicar a espera entre tentativas de consulta de status |

`PortabilityApplicationService` implementa os cinco ports de entrada e depende apenas dos ports de saída.

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
  `operation_installments`, coluna `position`) e `idempotency_key` única para reconhecer reenvios.
- `AuditEventRepositoryAdapter.append` é **append-only**: insert sem update/delete.
- `findById`, `findByProtocol` e `updateStatus` estão implementados; `updateStatus` altera somente `status`
  e `rejection_reason`. A listagem de eventos permanece stub (F06).
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
- `CentralClient`: chamadas HTTP com timeout e assinatura HMAC enviada no header `X-Signature`. No opt-in,
  o corpo exato é assinado; no registro da operação, o adapter assina o corpo canônico em pt-BR sem o
  campo `assinatura` e envia o HMAC também nesse campo. Qualquer resposta diferente da esperada (`201` no
  opt-in, `202` no registro), corpo ausente/inválido ou protocolo em branco vira
  `CentralUnavailableException` (`502`).
- Consulta de status: `CentralClient.consultStatus` faz um `GET` sem assinatura a
  `/operacoes-portabilidade/{protocolo}`. `204` vira `PENDING` com o `Retry-After` do cabeçalho; `200`
  traduz `CONFIRMADA`/`REJEITADA` e `motivoRejeicao`; status HTTP diferente de `200`/`204`, corpo
  inválido, status desconhecido ou `Retry-After` inválido viram `CentralUnavailableException` (`502`).
- Backoff: `getOperationStatus` consulta a Central no máximo três vezes enquanto a operação estiver
  `PENDING`. A espera antes da tentativa `n` é `min(5s, max(Retry-After, 1s × 2^(n-1)))`; `BackoffPort`
  aplica a espera e a política fica no caso de uso. Esgotadas as tentativas, a operação permanece
  `PENDING` e o `GET` responde `200` sem evento novo.
- Mock de status: as duas primeiras consultas a um protocolo respondem `204` com `Retry-After: 1`; a
  terceira responde `CONFIRMADA`. O contador é em memória e por protocolo; protocolo desconhecido responde
  `404`. A transição final usa `updateStatus` e anexa um evento `CONFIRMADA` ou `REJEITADA` com o protocolo
  e, quando houver, o motivo. Operação já finalizada não consulta a Central.
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
- Erros: falha de integração, timeout e resposta inesperada são traduzidos pelo adapter REST em respostas
  HTTP claras, exibidas no frontend. Eventos de falha de integração são F07.
- Transições de `OperationStatus` (`CONFIRMED`/`REJECTED`) e seus eventos são F04; a atualização da conta
  de recebimento após a confirmação é F05. A leitura da trilha na API e na interface é F06.

## Estratégia de testes

Cenários obrigatórios e comandos estão no [`../AGENTS.md`](../AGENTS.md), seções "Diretrizes para
testes" e "Verificação completa". Resumo: unitários de domínio/aplicação com fakes dos ports, `MockMvc`
para contratos HTTP, Vitest + Testing Library para comportamento visível da UI.
