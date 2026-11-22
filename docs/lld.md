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
| `DiscountInstallment` | `installmentId`, `agreementId`, `amount` (`BigDecimal`), `discountDate` (`LocalDate`), `status` |
| `ReceivingAccount` | `bank`, `agency`, `account` |
| `PortabilityOperation` | `operationId`, `beneficiaryId`, `installmentIds`, `creditorAssignee`, `receivingAccount`, `status`, `protocol`, `rejectionReason`, `requestedAt` |
| `AuditEvent` | `eventId`, `operationId`, `eventType`, `message`, `occurredAt` |
| `CentralOperationStatus` | `status`, `rejectionReason` |

`Authorization` e `DiscountInstallment` validam campos obrigatórios no construtor.

Enums: `OperationStatus` (`PENDING`, `CONFIRMED`, `REJECTED`), `InstallmentStatus` (`OPEN`, `PORTED`,
`CANCELLED`).

## Ports

### Entrada (`domain/ports/in`)

| Port | Método |
| --- | --- |
| `RegisterAuthorizationUseCase` | `registerAuthorization(beneficiaryId, agreementId): AuthorizationResult` |
| `GetDiscountAgendaUseCase` | `getAgenda(beneficiaryId): List<DiscountInstallment>` |
| `StartPortabilityUseCase` | `startPortability(beneficiaryId, installmentIds, creditorAssignee, receivingAccount): PortabilityOperation` |
| `GetOperationStatusUseCase` | `getOperationStatus(operationId): Optional<PortabilityOperation>` |
| `GetOperationAuditUseCase` | `getAuditTrail(operationId): List<AuditEvent>` |

### Saída (`domain/ports/out`)

| Port | Implementado por | Finalidade |
| --- | --- | --- |
| `AuthorizationRepository` | `AuthorizationRepositoryAdapter` | salvar/buscar autorização por beneficiário e convênio; verificar existência por beneficiário |
| `CentralAuthorizationPort` | `CentralClient` | montar payload/HMAC, registrar o opt-in na Central e devolver o `autorizacaoId` |
| `OperationRepository` | `OperationRepositoryAdapter` | salvar/buscar operação por id e por protocolo (idempotência) |
| `AuditEventRepository` | `AuditEventRepositoryAdapter` | anexar eventos e listar por operação |
| `DiscountAgendaPort` | `CentralClient` | buscar agenda na Central |
| `CentralPortabilityPort` | `CentralClient` | registrar operação e consultar status por protocolo |
| `SignaturePort` | `HmacSignatureService` | assinar e verificar mensagens |

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
- `OperationJpaEntity` usa `@ElementCollection` para `installmentIds` (tabela `operation_installments`).
- `AuditEventRepositoryAdapter.append` deve ser **append-only**: insert sem update/delete.
- `findByProtocol` existe para suportar idempotência (F03).
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
- `CentralClient`: chamadas HTTP com timeout, assinatura HMAC enviada no header `X-Signature` sobre o
  corpo exato e leitura de `Retry-After`; protocolo `202` e consulta de status com backoff.
- A Central valida a assinatura do opt-in: `201` com `autorizacaoId`, `400` para payload inválido e `401`
  para assinatura ausente/inválida. Na API, falha de integração com a Central vira `502`.
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
para contratos HTTP, Vitest + Testing Library para comportamento visível da UI.
