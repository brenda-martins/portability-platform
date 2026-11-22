# PRD — F03 Selecionar parcelas e registrar a portabilidade

> **Dono:** requisitos de produto desta funcionalidade. Escopo, fases e métricas em
> [`../../product-brief.md`](../../product-brief.md); termos em [`../../GLOSSARY.md`](../../GLOSSARY.md).

## Problema

Depois de ver a agenda (F02), o operador precisa escolher quais descontos serão portados e informar a
conta que passará a recebê-los. O registro precisa chegar à Central de Convênios de forma íntegra e
idempotente: um duplo clique ou retry não pode criar duas operações, e a seleção precisa ser válida no
momento do envio — uma parcela já portada ou com data passada não pode entrar na solicitação.

## Fluxo do usuário

1. Com a autorização e a agenda carregadas, o operador seleciona uma ou mais parcelas abertas e futuras.
2. Informa o credor cessionário e os dados da conta de recebimento (banco, agência e conta).
3. A plataforma valida a seleção contra a agenda atual da Central e envia a solicitação assinada.
4. A Central devolve um protocolo; a plataforma guarda a operação como `PENDING` e registra o evento de
   solicitação na trilha de auditoria.
5. A plataforma exibe o identificador da operação, o protocolo e o status `PENDING`.
6. Se a mesma solicitação for reenviada, a plataforma mostra a operação já registrada, sem criar outra
   e sem chamar a Central novamente.
7. Seleção inválida, falta de autorização ou falha da Central exibem o motivo e não criam operação.

## Histórias

- **Como operador**, quero selecionar parcelas elegíveis e informar a conta de recebimento para iniciar
  a portabilidade.
- **Como operador**, quero ver o protocolo devolvido pela Central para acompanhar a operação.
- **Como usuário**, quero que um reenvio por duplo clique não gere operação duplicada.
- **Como auditor**, quero que a solicitação seja registrada como evento imutável da operação.

## Critérios de aceite

- [ ] `POST /api/portability` com seleção válida responde `201` com a operação `PENDING`, protocolo e
      `requestedAt`; a Central recebe a solicitação assinada e responde `202` com `protocolo`.
- [ ] O mesmo corpo reenviado responde `200` com a operação existente, sem nova chamada à Central, sem
      nova operação e sem novo evento de auditoria.
- [ ] Parcelas que não existem na agenda da Central, que não estejam `OPEN` ou cuja data de desconto não
      seja futura são rejeitadas com `400`, sem chamar o registro na Central.
- [ ] Seleção vazia, identificadores em branco, credor em branco ou conta incompleta são rejeitados com
      `400`; beneficiário sem autorização é rejeitado com `403`.
- [ ] Falha de comunicação, timeout ou resposta inesperada da Central responde `502` e não persiste
      operação nem evento de auditoria.
- [ ] A Central simulada rejeita assinatura ausente/inválida com `401`, payload inválido com `400` e
      devolve o mesmo protocolo para o mesmo payload.
- [ ] O painel exibe protocolo e status após o registro e mostra a mensagem de erro da API quando o
      registro falha.

## Fora de escopo

- Consulta de status com retry e backoff (F04) e atualização da conta após confirmação (F05).
- Consulta da trilha de auditoria na interface (F06) — a F03 apenas grava o evento de solicitação.
- Rejeição aleatória, atrasos e falhas ocasionais da Central (F07).
- Alteração ou cancelamento de operação e assinatura digital real.
