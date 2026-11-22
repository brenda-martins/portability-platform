# Spec — F07 Tratar e exibir rejeições e falhas de integração com a Central

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP em [`../../README.md`](../../README.md).

## Escopo

Completa os cenários de rejeição e falha da Central: gatilho determinístico no mock, evento de auditoria para
falhas com operação existente e testes dos caminhos já traduzidos pelo adapter e pela UI. Não muda contrato
HTTP da aplicação nem cria port, DTO, tabela ou dependência.

## Contratos

### API da aplicação (`/api`)

Nenhum endpoint novo. Mantêm-se os códigos já documentados no README:

| Situação | Resposta | Efeito na trilha |
| --- | --- | --- |
| Central confirma | `200` com a operação `CONFIRMED` | evento `CONFIRMADA` |
| Central rejeita | `200` com a operação `REJECTED` e `rejectionReason` | evento `REJEITADA` com o motivo |
| Central falha ao consultar status | `502` com `{ "message": "..." }` | evento `FALHA_INTEGRACAO` |
| Central falha ao atualizar conta | `502` com `{ "message": "..." }` | evento `FALHA_INTEGRACAO` |
| Central falha no opt-in, agenda ou registro | `502` com `{ "message": "..." }` | nenhum evento: não há operação |

O evento `FALHA_INTEGRACAO` usa o mesmo formato de `AuditEvent` (`eventId`, `operationId`, `eventType`,
`message`, `occurredAt`), é append-only e não altera `status`, `rejectionReason` nem conta. A mensagem começa
com o contexto da chamada ("Falha ao consultar status na Central" ou "Falha ao atualizar conta na Central"),
seguido da mensagem da exceção traduzida pelo adapter.

### Central simulada (`/central`)

O desfecho é escolhido no registro, a partir do valor de `credorCessionario` (sem acentos, comparação
case-insensitive), e guardado por protocolo:

| Marcador no credor | Desfecho nas consultas de status |
| --- | --- |
| contém `REJEITAR` | duas respostas `204` com `Retry-After: 1`, depois `200` com `{ "status": "REJEITADA", "motivoRejeicao": "..." }` |
| contém `FALHA` | `503` sem corpo em toda consulta, sem contador e sem transição |
| nenhum | duas respostas `204` com `Retry-After: 1`, depois `200` com `CONFIRMADA` (comportamento atual) |

`REJEITAR` tem precedência se os dois marcadores aparecerem juntos. O registro sempre responde `202` com
protocolo determinístico pelo payload: a falha só acontece no acompanhamento, para que exista uma operação
à qual anexar o evento. O `503` exercita a mesma tradução para `CentralUnavailableException` usada por
timeouts e respostas inesperadas; o `CentralClient` mantém o read timeout de 2 segundos.

## Decisões de implementação

- `MockCentralController` guarda um enum interno de desfecho por protocolo em `ConcurrentHashMap`, ao lado
  dos contadores já existentes. Nenhum estado novo é exposto e nenhuma dependência é acrescentada.
- `CentralClient.consultStatus` continua traduzindo `REJEITADA` com motivo e qualquer resposta diferente de
  `200`/`204` em `CentralUnavailableException`; o `503` do mock não pede alteração no adapter.
- `PortabilityApplicationService` captura `CentralUnavailableException` apenas ao redor da chamada ao port
  da Central em `followStatus` e em `updateReceivingAccount`, anexa `FALHA_INTEGRACAO` e propaga a exceção.
  Capturas locais evitam rotular falhas de repositório como falha de integração. Em `startPortability`,
  `registerAuthorization` e `getAgenda` nada é anexado: a Central falha antes de existir uma operação.
- A rejeição já é traduzida em F04 (`updateStatus` com motivo e evento `REJEITADA`); a F07 a torna
  reproduzível e cobre o caminho completo com testes, sem alterar essa lógica.
- O frontend não muda: `PortabilityPanel` já mostra `rejectionReason` e mensagens de erro `502`, e
  `AuditPanel` já exibe qualquer tipo de evento devolvido pela API.

## Decisões de teste

- `MockCentralFailureScenariosTest` (novo, controller em processo): rejeição com motivo após duas consultas
  pendentes, `503` no marcador de falha e correspondência ignorando caixa.
- `CentralClientTest`: `503` na consulta de status vira `CentralUnavailableException`.
- `PortabilityApplicationServiceTest`: falha na consulta de status e na atualização de conta anexa
  `FALHA_INTEGRACAO` com o ID da operação, sem `updateStatus`/`updateReceivingAccount` e sem backoff; falha
  no registro continua sem operação e sem evento; rejeição continua gravando motivo e evento `REJEITADA`.
- `App.test.tsx`: mensagem `502` na consulta de status com a operação ainda `PENDING` e evento
  `FALHA_INTEGRACAO` visível na trilha; rejeição com motivo já coberta permanece.
