# Spec — F03 Selecionar parcelas e registrar a portabilidade

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP e idioma das mensagens em
> [`../../README.md`](../../README.md), seção "Documentação da API".

## Escopo

DTO e validação na borda, caso de uso com idempotência e validação de elegibilidade, persistência da
operação, evento de auditoria, integração assinada com a Central, mock da Central, endpoint da API e
testes. Consulta de status (F04), atualização de conta (F05) e leitura da trilha (F06) são casos de uso
separados, com as próprias specs. As decisões comuns — transações, versão do agregado, erros, correlation
id, métricas e assemblagem — estão em [`../../lld.md`](../../lld.md).

## Contratos

### API da aplicação

| Método | Rota | Situação | Resposta |
| --- | --- | --- | --- |
| `POST` | `/api/portability` | solicitação nova | `201` com `PortabilityOperation` |
| `POST` | `/api/portability` | reenvio da mesma solicitação | `200` com a operação existente |
| `POST` | `/api/portability` | seleção ou campos inválidos | `400` com `{ "message": "...", "fields": {...} }` |
| `POST` | `/api/portability` | beneficiário sem autorização | `403` com `{ "message": "..." }` |
| `POST` | `/api/portability` | chave de idempotência em conflito | `409` com `{ "message": "..." }` |
| `POST` | `/api/portability` | falha na Central | `502` com `{ "message": "..." }` |

A chave é reservada antes da chamada à Central. Reenvios concorrentes da mesma solicitação devolvem `200`
com a operação existente, sem registrar novo protocolo; a reserva é desfeita se a integração falhar.

Corpo da solicitação:

```json
{
  "beneficiaryId": "BEN-123",
  "installmentIds": ["INS-1"],
  "creditorAssignee": "Nova Credora S.A.",
  "receivingAccount": { "bank": "001", "agency": "1234", "account": "56789-0" }
}
```

Corpo de sucesso: `{ "operationId": "OPE-...", "beneficiaryId": "BEN-123",
"installmentIds": ["INS-1"], "creditorAssignee": "...", "receivingAccount": {...},
"status": "PENDING", "protocol": "PRT-...", "rejectionReason": null,
"requestedAt": "2026-11-22T12:34:56Z" }`. A `version` do agregado não é serializada.

### Central simulada

`POST /central/operacoes-portabilidade` recebe o corpo em pt-BR do PDF, incluindo `assinatura`, e o
cabeçalho `X-Signature`. Responde `202` com `{ "protocolo": "PRT-..." }`, `400` para payload inválido e
`401` para assinatura ausente/inválida. O protocolo é `PRT-` seguido do UUID determinístico gerado a
partir do corpo canônico (o corpo sem `assinatura`), de modo que um mesmo payload produz sempre o mesmo
protocolo.

### Assinatura

`CentralRequestCodec`, no adapter, monta o corpo em pt-BR em ordem fixa (`beneficiarioId`, `parcelas`,
`credorCessionario`, `contaRecebimento`), serializa sem `assinatura` e usa esse **corpo canônico** como
entrada do HMAC-SHA256 em Base64. O valor calculado vai no campo `assinatura` e no header `X-Signature`
do corpo efetivamente enviado. O mock remove `assinatura` do corpo recebido, serializa o restante na mesma
ordem e verifica o HMAC. O mesmo codec é usado pelo opt-in (F01) e pela atualização de conta (F05).

### Ports e modelo

- `StartPortabilityUseCase.startPortability(StartPortabilityCommand)` devolve
  `PortabilityOperationResult(operation, created)`, espelhando `AuthorizationResult` da F01. O command
  reúne `beneficiaryId` (`BeneficiaryId`), `installmentIds`, `creditorAssignee` e `receivingAccount`.
- `CentralPortabilityPort.registerPortability(StartPortabilityCommand): Protocol` não recebe assinatura:
  a assinatura passa a ser responsabilidade do adapter, que conhece o contrato em pt-BR.
- `OperationRepository.findById(OperationId)` localiza a operação;
  `findByIdempotencyKey(String)` localiza o reenvio;
  `reserveIdempotencyKey(String)` reserva a chave antes da integração e sinaliza conflito com
  `DuplicateIdempotencyKeyException`; `save(PortabilityOperation, String)` insere a operação nova com a
  chave de idempotência e `save(PortabilityOperation)` persiste o estado completo com a versão esperada.
- `PortabilityOperationResult` é um record de domínio, sem framework. `PortabilityOperation` calcula a
  chave de idempotência, guarda a `version` e mantém as transições no domínio.

## Decisões de implementação

- `PortabilityRequest` e `ReceivingAccountRequest` são DTOs da borda cujo `validate()` chama as
  invariantes de `Validation` no domínio; não há Bean Validation duplicada. `BeneficiaryId`,
  `StartPortabilityCommand` e os demais records aplicam as mesmas invariantes no construtor. O controller
  chama `validate()` antes do caso de uso. `ApiExceptionHandler` devolve `400` com `fields` para
  `InvalidRequestException` e `409` para outras violações de unicidade; conflito de reserva da chave é
  resolvido no caso de uso como reenvio.
- `StartPortabilityService` recebe o command validado, calcula a chave SHA-256 em hex de
  `beneficiaryId|parcelas ordenadas|credor|banco|agência|conta` em
  `PortabilityOperation.idempotencyKey(command)`, consulta `findByIdempotencyKey` e reserva a chave em
  `idempotency_reservations`. Se a operação existir, devolve-a com `created = false` **antes**
  de qualquer chamada à Central. Se a reserva conflitar com outra transação já confirmada, uma nova
  transação lê a operação existente e devolve `created = false`. Caso contrário, verifica a autorização do
  beneficiário, busca a agenda em `DiscountAgendaPort`, exige que
  todas as parcelas selecionadas estejam presentes, sejam `OPEN` e tenham `discountDate` estritamente
  futuro, e rejeita duplicatas na seleção. A gravação usa `save(operation, idempotencyKey)`; a coluna
  `idempotency_key` é única.
- Com a seleção válida, o caso de uso chama `CentralPortabilityPort.registerPortability`, cria a
  `PortabilityOperation` com `operationId` gerado por `IdGenerator`, `status = PENDING`, protocolo
  devolvido, `requestedAt = Instant.now(clock)` e `version = 0`, salva no repositório e anexa um
  `AuditEvent` de tipo `SOLICITADA` com o protocolo na mensagem. Escrita e evento compartilham
  `TransactionPort`: falha ao anexar o evento desfaz a operação e a reserva. Falha da Central não grava
  operação nem evento.
- `CentralClient.registerPortability` usa o `RestClient` com timeout já configurado, converte qualquer
  resposta diferente de `202`, corpo ausente ou `protocolo` em branco em `CentralUnavailableException`,
  e traduz falhas de transporte/JSON da mesma forma.
- `MockCentralController.registerPortability` valida assinatura (`401`), campos (`400`) e devolve `202`
  com protocolo determinístico. O desfecho das consultas futuras é guardado por protocolo a partir do
  marcador no credor (F07).
- `OperationRepositoryAdapter` implementa `save` e `findByIdempotencyKey`, com conversão explícita entre
  `PortabilityOperation` e `OperationJpaEntity`, incluindo a coleção `operation_installments`, com
  `@OrderColumn` para preservar a ordem da seleção.
- `AuditEventRepositoryAdapter.append` implementa insert append-only.
- `PortabilityController` implementa o `POST`, injetando `StartPortabilityUseCase` e devolvendo o DTO
  `OperationResponse`. Frontend: o DTO `PortabilityRequest` e `PortabilityPanel` passam a enviar
  `installmentIds` (o nome antigo `installments` não existe mais no contrato); o painel já exibe
  protocolo/status. Acrescenta-se um teste para a mensagem de erro da API no registro.

## Decisões de teste

- `StartPortabilityServiceTest` (unitário, fakes dos ports): criação com evento, reserva antes da chamada
  à Central, conflito de reserva devolvendo a operação existente sem chamar a Central, reenvio sem gravar
  evento, chave independente da ordem das parcelas, beneficiário sem autorização,
  parcela inexistente/não aberta/passada, seleção vazia, campos em branco, falha da Central sem gravação
  e rollback quando a gravação reporta concorrência. `StartPortabilityIdempotencyConcurrencyTest` (H2)
  cobre duas requisições idênticas simultâneas: uma criação, um reenvio e uma única chamada à Central.
  `OperationTransactionalTest` cobre a atomicidade com Spring.
- `CentralClientTest` (HTTP em processo): corpo assinado, `X-Signature`, protocolo devolvido e erro da
  Central.
- `PortabilityControllerContractTest` (`MockMvc`, caso de uso mockado): `201`, `200`, `400`, `409` e
  `502`, com o corpo de erro no padrão `{ "message": "..." }`.
- `OperationRepositoryAdapterTest` e `AuditEventRepositoryAdapterTest` (`@SpringBootTest` com o perfil
  `test`/H2 e `@Transactional`): gravação, leitura por chave de idempotência, reconstrução da coleção de
  parcelas, checagem de versão e append sem sobrescrever eventos anteriores. `@DataJpaTest` não está
  disponível no classpath do Spring Boot 4.2 deste projeto.
- `MockCentralPortabilityTest` (em processo): `202` com protocolo determinístico, `401` sem assinatura,
  `400` com payload inválido e igualdade de protocolo para payloads iguais.
- `App.test.tsx`: mensagem de erro devolvida pela API quando o registro falha; demais comportamentos do
  painel já estão cobertos.

Nenhuma dependência nova: SHA-256 e `MessageDigest` são do JDK; Jackson e as bibliotecas de teste já
estão no projeto.
