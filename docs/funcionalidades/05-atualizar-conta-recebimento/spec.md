# Spec — F05 Atualizar a conta de recebimento após a confirmação

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP em [`../../README.md`](../../README.md).

## Escopo

Uma fatia vertical: novo caso de uso, port de entrada e método nos ports de saída já existentes,
integração assinada com a Central, mock da Central, persistência da conta, evento de auditoria, endpoint da
API, formulário no painel de portabilidade e testes. F06 e F07 permanecem como stubs honestos.

## Contratos

### API da aplicação

| Método | Rota | Situação | Resposta |
| --- | --- | --- | --- |
| `PUT` | `/api/portability/{operationId}/receiving-account` | conta atualizada ou igual à atual | `200` com `PortabilityOperation` |
| `PUT` | `/api/portability/{operationId}/receiving-account` | campos em branco | `400` com `{ "message": "..." }` |
| `PUT` | `/api/portability/{operationId}/receiving-account` | operação inexistente | `404` |
| `PUT` | `/api/portability/{operationId}/receiving-account` | status diferente de `CONFIRMED` | `409` com `{ "message": "..." }` |
| `PUT` | `/api/portability/{operationId}/receiving-account` | falha na Central | `502` com `{ "message": "..." }` |

O corpo da requisição reusa `ReceivingAccountRequest` — `{ "bank": "...", "agency": "...", "account": "..." }`,
com `@NotBlank` nos três campos. O corpo de sucesso é o mesmo `PortabilityOperation` do README, com a conta
atualizada; nenhum campo novo é acrescentado ao contrato da aplicação.

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
  com `central.hmac-secret`; o mesmo valor também vai no cabeçalho `X-Signature`.
- `200` com `{ "banco", "agencia", "conta" }` quando o protocolo existe e os dados são válidos; o mock
  substitui a conta associada ao protocolo (em memória).
- `400` para payload incompleto, `401` para assinatura ausente/inválida e `404` para protocolo desconhecido.

### Ports e modelo

- Novo port de entrada `UpdateReceivingAccountUseCase.updateReceivingAccount(operationId, receivingAccount):
  Optional<PortabilityOperation>`. `Optional.empty()` significa operação inexistente.
- `CentralPortabilityPort` ganha `updateReceivingAccount(protocol, receivingAccount)`. Mantém-se um único port
  para a integração com operações na Central, com o mesmo padrão de assinatura do registro.
- `OperationRepository` ganha `updateReceivingAccount(operationId, receivingAccount)`, que altera somente
  `bank`, `agency` e `account` e devolve a operação reconstruída.
- Nova exceção de domínio `OperationNotConfirmedException`, traduzida por `ApiExceptionHandler` em `409`.
- Nenhuma coluna, tabela ou dependency nova: os campos da conta já existem em `portability_operations`.

## Decisões de implementação

- `PortabilityApplicationService` implementa `UpdateReceivingAccountUseCase`. Depois de validar os campos,
  busca a operação; se não existir, devolve `Optional.empty()`. Se o status não for `CONFIRMED`, lança
  `OperationNotConfirmedException`. Se a conta for igual à atual, devolve a operação sem chamar a Central,
  sem gravar e sem anexar evento.
- Quando há mudança, a aplicação chama a Central **antes** de persistir, seguindo a regra já usada no
  registro: falha de integração não altera o estado local nem a trilha. Depois do aceite da Central, chama
  `updateReceivingAccount` no repositório e anexa um `AuditEvent` com tipo `CONTA_ATUALIZADA` e mensagem com
  o protocolo e a nova conta. A chamada à Central e a gravação local não formam uma transação distribuída;
  essa é uma limitação aceita do mock.
- `CentralClient` monta o payload em pt-BR com `protocolo` junto da conta, assina o corpo canônico sem
  `assinatura`, envia o HMAC no cabeçalho e no campo, e converte status diferente de `200` ou corpo inválido
  em `CentralUnavailableException` (`502`). O protocolo entra no corpo assinado para vincular a assinatura à
  operação.
- `MockCentralController` valida assinatura e payload, grava a conta por protocolo em um mapa em memória e
  devolve a conta aceita. Não há endpoint de leitura dessa conta: o teste em processo inspeciona a resposta
  e o estado do próprio mock.
- `PortabilityController` injeta o novo caso de uso e responde com `ResponseEntity.of(...)`.
- `PortabilityPanel` abre um segundo formulário somente para operação `CONFIRMED`, com os campos
  preenchidos a partir de `operation.receivingAccount`, botão "Atualizar conta" e os mesmos estilos de
  carregamento/erro dos demais painéis. O DTO `PortabilityOperation` passa a incluir `receivingAccount`,
  campo que o backend já devolve na F03.
- O botão não esconde a chamada quando a conta é igual: a decisão de ignorar reenvios fica no backend, que é
  o dono da regra e da idempotência.

## Decisões de teste

- `PortabilityApplicationServiceTest`: conta alterada com chamada assinada à Central, persistência e evento;
  conta igual sem chamada/evento/gravação; `PENDING` e `REJECTED` com `OperationNotConfirmedException` e sem
  efeitos; operação inexistente com `Optional.empty()`; falha da Central sem gravação nem evento.
- `CentralClientTest`: `PUT` no caminho correto, com protocolo no corpo assinado e HMAC no cabeçalho;
  `200` traduzido para a conta; `400`/`401`/corpo inválido viram `CentralUnavailableException`.
- `MockCentralReceivingAccountTest` (em processo): conta atualizada com sucesso, `404` para protocolo
  desconhecido, `401` para assinatura inválida e `400` para payload incompleto.
- `OperationRepositoryAdapterTest`: atualização preserva status, protocolo, parcelas, credor, chave de
  idempotência e `requestedAt`.
- `PortabilityControllerContractTest`: `200`, `400`, `404`, `409` e `502` no `PUT` por id.
- `App.test.tsx`: formulário visível somente quando `CONFIRMED`, `PUT` com o corpo correto, nova conta
  exibida e mensagem de erro da API mostrada no painel.
