# PRD — F04 Acompanhar a confirmação assíncrona com retry e backoff

> **Dono:** requisitos de produto desta funcionalidade. Escopo, fases e métricas em
> [`../../product-brief.md`](../../product-brief.md); termos em [`../../GLOSSARY.md`](../../GLOSSARY.md);
> status em [`../../roadmap.md`](../../roadmap.md).

## Problema

Depois do registro (F03), a operação fica `PENDING`: a Central de Convênios devolve o protocolo na hora,
mas a confirmação chega depois. Enquanto isso, o operador precisa consultar o status sem criar outra
operação, sem duplicar eventos e sem precisar saber como a Central sinaliza "ainda pendente" (`204` com
`Retry-After`). A consulta precisa esperar o intervalo indicado pela Central e aumentar a espera entre
tentativas, sem travar a operação caso a confirmação não chegue a tempo.

## Fluxo do usuário

1. Com a operação registrada, o operador vê o protocolo e o status `PENDING`.
2. O operador aciona "Consultar status" (ou repete a consulta depois).
3. A plataforma consulta a Central pelo protocolo; se ainda estiver pendente, espera o `Retry-After` com
   backoff e tenta novamente, até um número limitado de tentativas.
4. Ao receber a confirmação, a plataforma atualiza a operação, grava um evento imutável de mudança de
   status e exibe o novo status.
5. Se a Central devolver rejeição, a plataforma grava o motivo, exibe `REJECTED` e o motivo da rejeição.
6. Se a confirmação não chegar dentro das tentativas, a operação continua `PENDING`, sem evento novo; uma
   consulta posterior pode retomar o acompanhamento.
7. Operação inexistente ou falha de integração exibem, respectivamente, "não encontrada" e o erro da
   Central, sem alterar o estado local.

## Histórias

- **Como operador**, quero consultar o status pelo identificador da operação para acompanhar a
  confirmação sem reenviar a solicitação.
- **Como operador**, quero que a plataforma respeite o `Retry-After` da Central para não sobrecarregar a
  integração enquanto a confirmação não chega.
- **Como operador**, quero ver o status final e o motivo da rejeição quando a Central responder.
- **Como auditor**, quero que cada mudança de status seja registrada como evento imutável da operação.

## Critérios de aceite

- [ ] `GET /central/operacoes-portabilidade/{protocolo}` responde `204` com `Retry-After` nas duas
      primeiras consultas de um protocolo e `200` com `{ "status": "CONFIRMADA", "motivoRejeicao": null }`
      na terceira; protocolo desconhecido responde `404`.
- [ ] `GET /api/portability/{operationId}` com operação `PENDING` consulta a Central, aplica backoff
      respeitando o `Retry-After`, persiste `CONFIRMED`, anexa um evento `CONFIRMADA` e responde `200` com
      a operação atualizada.
- [ ] O intervalo entre tentativas cresce progressivamente e é aplicado no backend; nenhuma espera é
      feita após a última tentativa.
- [ ] Consulta de operação já `CONFIRMED` ou `REJECTED` responde `200` sem chamar a Central e sem gravar
      evento.
- [ ] Se a Central devolver `REJEITADA`, a operação passa a `REJECTED`, guarda `motivoRejeicao`, anexa
      evento `REJEITADA` e a interface exibe o motivo. A geração aleatória de rejeições permanece em F07.
- [ ] Esgotadas as tentativas com a Central ainda pendente, a resposta é `200` com a operação `PENDING`,
      sem evento de confirmação.
- [ ] `operationId` inexistente responde `404`; falha, timeout ou resposta inesperada da Central responde
      `502`, sem mudar o status e sem gravar evento.
- [ ] O botão "Consultar status" da interface busca `GET /api/portability/{operationId}` e exibe o status
      devolvido, inclusive o motivo da rejeição.

## Fora de escopo

- Atualização da conta de recebimento após a confirmação (F05).
- Exposição da trilha de auditoria na API e na interface (F06) — a F04 apenas acrescenta eventos à trilha.
- Rejeição aleatória, atrasos imprevisíveis e falhas ocasionais da Central (F07).
- Processamento em segundo plano, filas, notificações push ou polling automático na interface.
