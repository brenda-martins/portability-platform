# Spec — F06 Consultar a trilha de auditoria de cada operação

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP em [`../../README.md`](../../README.md).

## Escopo

Port de entrada, caso de uso, repositório de eventos, endpoint da API e painel de auditoria. As decisões
comuns — erros, correlation id, métricas e assemblagem — estão em [`../../lld.md`](../../lld.md).

## Contratos

### API da aplicação

| Método | Rota | Situação | Resposta |
| --- | --- | --- | --- |
| `GET` | `/api/portability/{operationId}/audit` | operação encontrada | `200` com `AuditEvent[]` |
| `GET` | `/api/portability/{operationId}/audit` | operação existente sem eventos | `200` com `[]` |
| `GET` | `/api/portability/{operationId}/audit` | `operationId` em branco | `400` com `{ "message": "...", "fields": {...} }` |
| `GET` | `/api/portability/{operationId}/audit` | operação inexistente | `404` sem corpo |

Cada item do array tem `{ "eventId", "operationId", "eventType", "message", "occurredAt" }`, com
`occurredAt` em ISO-8601. A lista vem ordenada por `occurredAt` crescente; eventos com o mesmo
`occurredAt` não têm ordem relativa garantida, pois não há coluna sequencial. Não há corpo na requisição
nem parâmetros de consulta.

### Ports e modelo

- `GetOperationAuditUseCase.getAuditTrail(OperationId): Optional<List<AuditEvent>>`: `Optional.empty()`
  significa operação inexistente; `Optional.of(List.of())` significa operação existente sem eventos.
- `AuditEventRepository.findByOperationId(OperationId)` devolve os eventos da operação em ordem de
  ocorrência. `append` continua sendo a única escrita, sem atualização ou remoção.
- `AuditEventType` reúne as constantes (`REQUESTED`, `CONFIRMED`, `REJECTED`, `INTEGRATION_FAILURE`,
  `ACCOUNT_UPDATED`), cada uma com `externalValue()` que preserva os valores do contrato
  (`SOLICITADA`, `CONFIRMADA`, `REJEITADA`, `FALHA_INTEGRACAO`, `CONTA_ATUALIZADA`);
  `AuditEvent.eventType` permanece `String` para preservar a coluna e o contrato.
- Nenhuma coluna, tabela ou dependência nova: `AuditEvent` já existe no domínio e no frontend, e a tabela
  `audit_events` já está no README.

## Decisões de implementação

- `GetOperationAuditService` valida o `operationId`, verifica a existência da operação com
  `OperationRepository.findById` e delega a leitura a `AuditEventRepository`. A verificação evita
  confundir "operação inexistente" com "operação sem eventos". A consulta não chama a Central nem grava,
  e registra métrica no seam.
- `AuditEventRepositoryAdapter.findByOperationId` usa
  `SpringDataAuditEventRepository.findByOperationIdOrderByOccurredAtAsc`, já existente, e converte cada
  entidade JPA para o record `AuditEvent`. Nenhum método de escrita é acrescentado.
- `AuditController` injeta `GetOperationAuditUseCase` e responde com `ResponseEntity.of(...)`, o mesmo
  padrão de `GET /api/portability/{operationId}`, produzindo `200` ou `404` sem exceção própria. A
  resposta usa o DTO `AuditEventResponse`.
- `AuditPanel` já existe, usa `apiFetch` e mantém os estados de carregamento, erro, vazio e ordenação
  visível. A F06 acrescenta a limpeza da trilha mostrada no início de cada consulta, para que uma falha
  não deixe eventos de outra operação na tela.
- A ordem de exibição vem do backend; o frontend continua ordenando por `occurredAt` como defesa contra
  respostas fora de ordem.

## Decisões de teste

- `GetOperationAuditServiceTest`: trilha devolvida na íntegra para operação existente, sem chamadas à
  Central nem `append`; `Optional.empty()` para operação inexistente, sem consultar eventos; ID em branco
  rejeitado antes de qualquer port.
- `AuditEventRepositoryAdapterTest`: leitura por operação devolve somente os eventos daquela operação,
  convertidos para o domínio e ordenados por `occurredAt`; a ordem entre eventos com o mesmo instante não
  é verificada.
- `AuditControllerContractTest` (com port mockado): `200` com os campos do evento, `200` com lista vazia
  e `404` para operação inexistente.
- `App.test.tsx`: consulta usa o ID da operação registrada, eventos e estado vazio são exibidos, erro da
  API é mostrado e a trilha anterior é limpa quando uma nova consulta falha.
