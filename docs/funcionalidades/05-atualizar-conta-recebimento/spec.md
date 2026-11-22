# Spec — F05 Atualizar a conta de recebimento após a confirmação

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP em [`../../README.md`](../../README.md).

## Escopo

Novo caso de uso, port de entrada e método nos ports de saída já existentes, integração assinada com a
Central, mock da Central, persistência da conta, evento de auditoria, endpoint da API, formulário no
painel de portabilidade e testes. As decisões comuns — transações, versão do agregado, erros, correlation
id, métricas e assemblagem — estão em [`../../lld.md`](../../lld.md).

## Contratos

### API da aplicação

| Método | Rota | Situação | Resposta |
| --- | --- | --- | --- |
| `PUT` | `/api/portability/{operationId}/receiving-account` | conta atualizada ou igual à atual | `200` com `PortabilityOperation` |
| `PUT` | `/api/portability/{operationId}/receiving-account` | campos em branco | `400` com `{ "message": "...", "fields": {...} }` |
| `PUT` | `/api/portability/{operationId}/receiving-account` | operação inexistente | `404` |
| `PUT` | `/api/portability/{operationId}/receiving-account` | status diferente de `CONFIRMED` | `409` com `{ "message": "..." }` |
| `PUT` | `/api/portability/{operationId}/receiving-account` | versão desatualizada | `409` com `{ "message": "..." }` |
| `PUT` | `/api/portability/{operationId}/receiving-account` | falha na Central | `502` com `{ "message": "..." }` |

O corpo da requisição reusa `ReceivingAccountRequest` — `{ "bank": "...", "agency": "...", "account": "..." }`.
O corpo de sucesso é o mesmo `PortabilityOperation` do README, com a conta atualizada; nenhum campo novo
é acrescentado ao contrato da aplicação e a `version` do agregado não é serializada.

### Central simulada

O PDF não fixa um endpoint para essa atualização; o contrato abaixo é uma extensão do mock, desenhada no
mesmo padrão do registro (F03):

`PUT /central/operacoes-portabilidade/{protocolo}/conta-recebimento`

```json
{
  "protocolo": "PRT-...",
  "banco": "001",
  "agencia": "1234",
  "conta": "56789-0",
  "assinatura": "..."
}
```

- `assinatura` é o HMAC-SHA256 em Base64 do corpo canônico **sem** o próprio campo `assinatura`, calculado
  com `central.hmac-secret`; o mesmo valor também vai no cabeçalho `X-Signature`. `CentralRequestCodec`
  monta e verifica o corpo.
- `200` com `{ "banco", "agencia", "conta" }` quando o protocolo existe e os dados são válidos; o mock
  substitui a conta associada ao protocolo (em memória).
- `400` para payload incompleto, `401` para assinatura ausente/inválida e `404` para protocolo desconhecido.

### Ports e modelo

- `UpdateReceivingAccountUseCase.updateReceivingAccount(OperationId, receivingAccount):
  Optional<PortabilityOperation>`; `Optional.empty()` significa operação inexistente.
- `CentralPortabilityPort` mantém um único port para a integração com operações na Central, com o mesmo
  padrão de assinatura do registro.
- `OperationRepository.findById` localiza a operação e `save(PortabilityOperation)` persiste o agregado
  inteiro com a versão esperada. Não há `updateReceivingAccount` por campo.
- `PortabilityOperation.withReceivingAccount` exige `CONFIRMED` e lança
  `OperationNotConfirmedException`; a borda HTTP traduz para `409`. Versão desatualizada gera
  `ConcurrentOperationException` (`409`).
- Nenhuma coluna, tabela ou dependência nova: os campos da conta já existem em `portability_operations`.

## Decisões de implementação

- `ReceivingAccountRequest.validate()` usa as invariantes de `Validation` no domínio; não há Bean
  Validation duplicada. O controller chama `validate()` antes do caso de uso.
- `UpdateReceivingAccountService` valida os campos e o id, busca a operação e devolve `Optional.empty()`
  quando não existe. Se o status não for `CONFIRMED`, `withReceivingAccount` lança
  `OperationNotConfirmedException`. Se a conta for igual à atual, devolve a operação sem chamar a Central,
  sem gravar e sem anexar evento.
- Quando há mudança, a aplicação chama a Central **antes** de persistir, seguindo a regra usada no
  registro: falha de integração não altera o estado local nem a trilha. Depois do aceite da Central,
  persiste com `save(operation)` e anexa um `AuditEvent` com tipo `CONTA_ATUALIZADA` e mensagem com o
  protocolo e a nova conta, na mesma unidade de trabalho (`TransactionPort`). `Clock`, `IdGenerator` e
  `MetricsPort` são injetados. A chamada à Central e a gravação local não formam uma transação
  distribuída; essa é uma limitação aceita do mock.
- `CentralClient` usa `CentralRequestCodec` para montar o payload em pt-BR com `protocolo` junto da conta,
  assinar o corpo canônico sem `assinatura`, enviar o HMAC no cabeçalho e no campo, e converter status
  diferente de `200` ou corpo inválido em `CentralUnavailableException` (`502`). O protocolo entra no
  corpo assinado para vincular a assinatura à operação.
- `MockCentralController` valida assinatura e payload, grava a conta por protocolo em um mapa em memória e
  devolve a conta aceita. Não há endpoint de leitura dessa conta: o teste em processo inspeciona o estado
  do mock por `accountFor(protocol)`.
- `PortabilityController` injeta o caso de uso e responde com `ResponseEntity.of(...)`, convertendo o
  resultado no DTO `OperationResponse`.
- `PortabilityPanel` abre um segundo formulário somente para operação `CONFIRMED`, com os campos
  preenchidos a partir de `operation.receivingAccount`, botão "Atualizar conta" e os mesmos estilos de
  carregamento/erro dos demais painéis. O DTO `PortabilityOperation` passa a incluir `receivingAccount`,
  campo que o backend já devolve na F03.
- O botão não esconde a chamada quando a conta é igual: a decisão de ignorar reenvios fica no backend, que
  é o dono da regra e da idempotência.

## Decisões de teste

- `UpdateReceivingAccountServiceTest`: conta alterada com chamada assinada à Central, persistência e
  evento; conta igual sem chamada/evento/gravação; `PENDING` e `REJECTED` com
  `OperationNotConfirmedException` e sem efeitos; operação inexistente com `Optional.empty()`; falha da
  Central com evento `FALHA_INTEGRACAO` e sem alterar a conta; conflito de versão com rollback.
- `CentralClientTest`: `PUT` no caminho correto, com protocolo no corpo assinado e HMAC no cabeçalho;
  `200` traduzido para a conta; `400`/`401`/corpo inválido viram `CentralUnavailableException`.
- `MockCentralReceivingAccountTest` (em processo): conta atualizada com sucesso e inspecionada por
  `accountFor`, `404` para protocolo desconhecido, `401` para assinatura inválida e `400` para payload
  incompleto.
- `OperationRepositoryAdapterTest`: atualização preserva status, protocolo, parcelas, credor, chave de
  idempotência e `requestedAt`, e confere a versão.
- `PortabilityControllerContractTest`: `200`, `400`, `404`, `409` e `502` no `PUT` por id.
- `App.test.tsx`: formulário visível somente quando `CONFIRMED`, `PUT` com o corpo correto, nova conta
  exibida e mensagem de erro da API mostrada no painel.
