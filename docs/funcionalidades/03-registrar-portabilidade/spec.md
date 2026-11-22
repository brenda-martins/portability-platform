# Spec — F03 Selecionar parcelas e registrar a portabilidade

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP em [`../../README.md`](../../README.md).

## Escopo

Uma fatia vertical: DTO e validação na borda, caso de uso com idempotência e validação de elegibilidade,
persistência da operação, evento de auditoria, integração assinada com a Central, mock da Central,
endpoint da API e testes. Consulta de status (F04), atualização de conta (F05) e leitura da trilha (F06)
continuam como stubs honestos.

## Contratos

### API da aplicação

| Método | Rota | Situação | Resposta |
| --- | --- | --- | --- |
| `POST` | `/api/portability` | solicitação nova | `201` com `PortabilityOperation` |
| `POST` | `/api/portability` | reenvio da mesma solicitação | `200` com a operação existente |
| `POST` | `/api/portability` | seleção ou campos inválidos | `400` com `{ "message": "..." }` |
| `POST` | `/api/portability` | beneficiário sem autorização | `403` com `{ "message": "..." }` |
| `POST` | `/api/portability` | falha na Central | `502` com `{ "message": "..." }` |

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
"requestedAt": "2026-11-22T12:34:56Z" }`.

### Central simulada

`POST /central/operacoes-portabilidade` recebe o corpo em pt-BR do PDF, incluindo `assinatura`, e o
cabeçalho `X-Signature`. Responde `202` com `{ "protocolo": "PRT-..." }`, `400` para payload inválido e
`401` para assinatura ausente/inválida. O protocolo é `PRT-` seguido do UUID determinístico gerado a
partir do corpo canônico (o corpo sem `assinatura`), de modo que um mesmo payload produz sempre o mesmo
protocolo.

### Assinatura

O adapter monta o corpo em pt-BR em ordem fixa (`beneficiarioId`, `parcelas`, `credorCessionario`,
`contaRecebimento`), serializa sem `assinatura` e usa esse **corpo canônico** como entrada do HMAC-SHA256
em Base64 de `SignaturePort`. O valor calculado vai no campo `assinatura` e no header `X-Signature` do
corpo efetivamente enviado. O mock remove `assinatura` do corpo recebido, serializa o restante na mesma
ordem e verifica o HMAC. Essa é a exceção documentada à regra "corpo exato" da F01: a assinatura não pode
estar incluída no próprio conteúdo assinado.

### Ports e modelo

- `StartPortabilityUseCase.startPortability(...)` passa a devolver
  `PortabilityOperationResult(operation, created)`, espelhando `AuthorizationResult` da F01.
- `CentralPortabilityPort.registerPortability(...)` perde o parâmetro `signature`: a assinatura passa a
  ser responsabilidade do adapter, que conhece o contrato em pt-BR.
- `OperationRepository.findByIdempotencyKey(String)` passa a existir para localizar o reenvio, e
  `save(PortabilityOperation, String)` passa a receber a chave de idempotência calculada pelo caso de uso.
- `PortabilityOperationResult` é um record de domínio, sem framework.

## Decisões de implementação

- `StartPortabilityRequest` e `ReceivingAccountRequest` são DTOs da borda com `@NotBlank`/`@NotEmpty` e
  `@Valid` no nested; o handler de `MethodArgumentNotValidException` passa a usar mensagem genérica
  válida para todos os endpoints.
- `PortabilityApplicationService.startPortability` valida os campos, calcula a chave de idempotência e
  consulta `OperationRepository.findByIdempotencyKey`. Se existir, devolve a operação com `created =
  false` **antes** de qualquer chamada à Central. Caso contrário, verifica a autorização do
  beneficiário, busca a agenda em `DiscountAgendaPort`, exige que todas as parcelas selecionadas estejam
  presentes, sejam `OPEN` e tenham `discountDate` estritamente futuro, e rejeita duplicatas na seleção.
  A chave é o SHA-256 em hex de `beneficiaryId|parcelas ordenadas|credor|banco|agência|conta`, gravada
  em `portability_operations.idempotency_key` (única).
- Com a seleção válida, o caso de uso chama `CentralPortabilityPort.registerPortability`, cria a
  `PortabilityOperation` com `operationId` UUID, `status = PENDING`, protocolo devolvido,
  `requestedAt = Instant.now()`, salva no repositório e anexa um `AuditEvent` de tipo `SOLICITADA` com o
  protocolo na mensagem. Falha da Central não grava operação nem evento; como ainda não existe operação,
  não há evento de falha nesta funcionalidade (F07).
- `CentralClient.registerPortability` usa o `RestClient` com timeout já configurado, converte qualquer
  resposta diferente de `202`, corpo ausente ou `protocolo` em branco em `CentralUnavailableException`,
  e traduz falhas de transporte/JSON da mesma forma.
- `MockCentralController.registerPortability` troca o `501`: valida assinatura (`401`), campos (`400`)
  e devolve `202` com protocolo determinístico. Os endpoints de status e os de operação já implementados
  permanecem inalterados.
- `OperationRepositoryAdapter` implementa somente `save` e `findByIdempotencyKey`, com conversão
  explícita entre `PortabilityOperation` e `OperationJpaEntity`, incluindo a coleção
  `operation_installments`, com `@OrderColumn` para preservar a ordem da seleção.
  `findById`/`findByProtocol` permanecem stubs de F04.
- `AuditEventRepositoryAdapter.append` implementa insert append-only; `findByOperationId` permanece
  stub de F06.
- `PortabilityController` implementa apenas o `POST`, injetando `StartPortabilityUseCase`; o `GET` de
  status continua stub de F04. Frontend: o DTO `PortabilityRequest` e `PortabilityPanel` passam a enviar
  `installmentIds` (o nome antigo `installments` não existe mais no contrato); o painel já exibe
  protocolo/status. Acrescenta-se um teste para a mensagem de erro da API no registro.

## Decisões de teste

- `PortabilityApplicationServiceTest` (unitário, mocks dos ports): criação com evento, reenvio sem
  chamar a Central nem gravar evento, beneficiário sem autorização, parcela inexistente/não aberta/passada,
  seleção vazia, campos em branco e falha da Central sem gravação.
- `CentralClientTest` (HTTP em processo): corpo assinado, `X-Signature`, protocolo devolvido e erro da
  Central.
- `PortabilityControllerContractTest` (`MockMvc`, caso de uso mockado): `201`, `200`, `400`, `403` e
  `502`, com o corpo de erro no padrão `{ "message": "..." }`.
- `OperationRepositoryAdapterTest` e `AuditEventRepositoryAdapterTest` (`@SpringBootTest` com o perfil
  `test`/H2 e `@Transactional`): gravação, leitura por chave de idempotência, reconstrução da coleção de
  parcelas e append sem sobrescrever eventos anteriores. `@DataJpaTest` não está disponível no classpath
  do Spring Boot 4.2 deste projeto.
- `MockCentralPortabilityTest` (em processo): `202` com protocolo determinístico, `401` sem assinatura,
  `400` com payload inválido e igualdade de protocolo para payloads iguais.
- `App.test.tsx`: mensagem de erro devolvida pela API quando o registro falha; demais comportamentos do
  painel já estão cobertos.

Nenhuma dependência nova: SHA-256 e `MessageDigest` são do JDK; Jackson, Bean Validation e as bibliotecas
de teste já estão no projeto.
