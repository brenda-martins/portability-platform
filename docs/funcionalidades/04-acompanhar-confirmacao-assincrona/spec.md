# Spec — F04 Acompanhar a confirmação assíncrona com retry e backoff

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP e idioma das mensagens em
> [`../../README.md`](../../README.md), seção "Documentação da API".

## Escopo

Leitura da operação, consulta sem assinatura à Central, interpretação de `Retry-After`, política de
backoff, persistência da transição, evento de auditoria, endpoint `GET /api/portability/{operationId}`,
mock da Central e testes. O frontend já tem o botão "Consultar status"; a F04 só cobre o contrato com o
endpoint. As decisões comuns — transações, versão do agregado, erros, correlation id, métricas e
assemblagem — estão em [`../../lld.md`](../../lld.md).

## Contratos

### API da aplicação

| Método | Rota | Situação | Resposta |
| --- | --- | --- | --- |
| `GET` | `/api/portability/{operationId}` | operação encontrada (inclusive ainda pendente) | `200` com `PortabilityOperation` |
| `GET` | `/api/portability/{operationId}` | `operationId` em branco | `400` com `{ "message": "...", "fields": {...} }` |
| `GET` | `/api/portability/{operationId}` | operação inexistente | `404` |
| `GET` | `/api/portability/{operationId}` | versão desatualizada | `409` com `{ "message": "..." }` |
| `GET` | `/api/portability/{operationId}` | falha na consulta à Central | `502` com `{ "message": "..." }` |

O corpo de sucesso é o mesmo `PortabilityOperation` da F03, com `status` atualizado e `rejectionReason`
preenchido quando aplicável. Nenhuma coluna ou campo novo é acrescentado ao contrato; a `version` do
agregado não é serializada.

### Central simulada

`GET /central/operacoes-portabilidade/{protocolo}` não exige assinatura nem corpo. O contador é em
memória, por protocolo:

| Consultas ao mesmo protocolo | Resposta |
| --- | --- |
| 1ª e 2ª | `204` sem corpo, com `Retry-After: 1` |
| 3ª em diante | `200` com `{ "status": "CONFIRMADA", "motivoRejeicao": null }` |
| protocolo nunca registrado | `404` |

A rejeição é mapeada quando a Central a devolve (`REJEITADA` + `motivoRejeicao`), mas o mock da F04 não a
gera; essa simulação pertence à F07.

### Política de retry e backoff

- Máximo de **3 consultas** por chamada ao endpoint da aplicação.
- A espera antes da tentativa `n` (com `n` começando em 1) é
  `min(5s, max(Retry-After informado pela Central, 1s × 2^(n-1)))`.
- Com o mock da F04, as esperas são `1s` e `2s`. Não há espera depois da última consulta.
- Se a terceira consulta ainda devolver `PENDENTE`, a operação permanece `PENDING` e a resposta é `200`;
  a próxima chamada retoma o acompanhamento.
- Uma interrupção durante o backoff restaura a flag do thread e devolve a operação como estava. A
  confirmação continua no caminho do `GET` (decisão de manter polling); não há worker nem evento
  assíncrono.

### Ports e modelo

- `CentralOperationStatus` é `record CentralOperationStatus(OperationStatus status, String
  rejectionReason, Duration retryAfter)`. `PENDING` representa o `204`/`PENDENTE`; `retryAfter` é
  `Duration.ZERO` para status finais ou quando a Central não informa o cabeçalho.
- `BackoffPort.await(Duration)` aplica a espera. O domínio continua sem framework; a política de backoff
  fica no caso de uso, e o adapter apenas dorme a thread.
- `OperationRepository.findById` localiza a operação; `save(PortabilityOperation)` persiste o agregado
  inteiro e confere a versão lida pelo chamador. Não existem `findByProtocol`, `updateStatus` nem
  atualizações por campo.
- `PortabilityOperation.transitionTo(status, rejectionReason)` controla a transição `PENDING ->
  CONFIRMED/REJECTED`, guarda o motivo e devolve um novo record com a mesma versão.

## Decisões de implementação

- `CentralClient.consultStatus` faz o `GET` pelo protocolo, converte `204` em `PENDING` com o
  `Retry-After` do cabeçalho, traduz `CONFIRMADA`/`REJEITADA` para o enum interno e converte status
  diferente de `200`/`204`, corpo inválido, status desconhecido ou cabeçalho inválido em
  `CentralUnavailableException`. A aplicação traduz a exceção em `502`, pela regra já existente.
- `GetOperationStatusService` recebe o `OperationId` validado no construtor do value object, busca a
  operação pelo id e devolve vazio quando não existe.
  Operação já finalizada não consulta a Central. Para operação pendente, executa o loop de até três
  consultas, usando `BackoffPort` entre tentativas. Ao receber status final, chama
  `transitionTo`, persiste com `save(operation)` e anexa um `AuditEvent` com tipo igual ao status final e
  mensagem com o protocolo (e o motivo, quando houver), tudo em uma unidade de trabalho. Um evento
  terminal já registrado não é duplicado; versão desatualizada vira `ConcurrentOperationException`
  (`409`). Falha da Central é registrada como `FALHA_INTEGRACAO` (F07) e propagada sem gravar transição.
- `MockCentralController.getStatus` usa um mapa em memória de protocolo para número de consultas,
  devolve `204` com `Retry-After: 1` nas duas primeiras e `200` com `CONFIRMADA` depois. Protocolo
  ausente do mapa responde `404`. Nenhum dado é inventado para protocolo desconhecido.
- `OperationRepositoryAdapter` implementa as leituras e `save(operation)` com conversão explícita entre
  entidade JPA e domínio, conferindo a versão esperada.
- `ThreadBackoffAdapter` (em `adapters/out/timing`) implementa `BackoffPort` com `Thread.sleep` e restaura
  o flag de interrupção. Nos testes unitários, um fake registra as durações sem dormir.
- `PortabilityController` passa a injetar `GetOperationStatusUseCase` e responder com
  `ResponseEntity.of(...)`, que produz `200` ou `404` sem exceção própria. A resposta usa o DTO
  `OperationResponse`, sem a `version` do agregado.
- Nenhuma dependência nova: `Duration`, `Thread.sleep` e as bibliotecas já presentes atendem à F04.

## Decisões de teste

- `GetOperationStatusServiceTest`: operação pendente que confirma após duas esperas (com as durações
  registradas pelo fake), operação final sem chamada à Central, rejeição com motivo e evento, esgotamento
  das tentativas mantendo `PENDING`, falha da Central sem gravação de transição, interrupção do backoff,
  evento terminal não duplicado, conflito de versão com rollback, id inexistente devolvendo vazio e id em
  branco rejeitado no construtor do value object. `OperationConfirmationConcurrencyTest` cobre a corrida
  de confirmação com H2, e `OperationTransactionalTest`, a atomicidade da transição com o evento.
- `CentralClientTest`: `204` + `Retry-After` viram `PENDING` com a duração correta, `200` traduz
  `CONFIRMADA` e `REJEITADA`, `500`/status desconhecido/cabeçalho inválido viram
  `CentralUnavailableException`, e o `GET` não envia assinatura.
- `MockCentralStatusTest` (em processo): duas respostas `204` com `Retry-After`, a terceira `200` com
  `CONFIRMADA` e `404` para protocolo desconhecido.
- `OperationRepositoryAdapterTest`: leitura por id, checagem de versão e persistência da transição
  preservando os demais campos.
- `PortabilityControllerContractTest`: `200`, `400`, `404`, `409` e `502` no `GET` por id;
  `GetOperationStatusIntegrationTest` exerce o caminho completo com H2.
- `App.test.tsx`: clique em "Consultar status" chama `GET /api/portability/{operationId}` e exibe o status
  atualizado, incluindo o motivo da rejeição.
