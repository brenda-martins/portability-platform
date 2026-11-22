# PRD — F05 Atualizar a conta de recebimento após a confirmação

> **Dono:** requisitos de produto desta funcionalidade. Escopo, fases e métricas em
> [`../../product-brief.md`](../../product-brief.md); termos em [`../../GLOSSARY.md`](../../GLOSSARY.md).
> Spec de origem: PDF do desafio, disponível fora do
> repositório; ele pede a atualização da conta após a confirmação, mas deixa o contrato da Central livre.

## Problema

A conta de recebimento é informada no registro (F03), enquanto a confirmação ainda está pendente. Depois que
a Central confirma a portabilidade (F04), o operador pode precisar corrigir os dados bancários que receberão
as parcelas. Sem uma etapa própria, essa correção exigiria criar outra operação — o que geraria duplicidade —
ou ficaria apenas no banco local, sem chegar à Central que passou a ser a credora das parcelas.

## Fluxo do usuário

1. Com a operação `CONFIRMED`, o operador vê a conta de recebimento atual.
2. Informa banco, agência e conta novos e aciona "Atualizar conta".
3. A plataforma valida os campos, envia a alteração assinada à Central pelo protocolo da operação e, se a
   Central aceitar, atualiza a conta local e registra um evento imutável na trilha.
4. A interface exibe a nova conta. Enquanto a operação não está confirmada, a atualização não é oferecida.
5. Se a conta enviada for igual à atual, nada é enviado nem registrado: a operação é devolvida como está.
6. Falha de integração com a Central, operação inexistente, status diferente de `CONFIRMED` e campos em
   branco são exibidos ao operador, sem alterar a conta local nem a trilha.

## Histórias

- **Como operador**, quero corrigir a conta de recebimento depois da confirmação para que os descontos sejam
  creditados na conta correta, sem criar uma nova operação.
- **Como operador**, quero que a alteração chegue à Central assinada pelo protocolo, para que o sistema
  externo e a plataforma fiquem com os mesmos dados.
- **Como auditor**, quero que a mudança de conta seja registrada como evento imutável da operação.
- **Como operador**, quero que um duplo clique com a mesma conta não gere eventos duplicados na trilha.

## Critérios de aceite

- [ ] O painel de portabilidade oferece "Atualizar conta" somente quando o status é `CONFIRMED`, com os
      campos preenchidos com a conta atual da operação.
- [ ] `PUT /api/portability/{operationId}/receiving-account` com `{ bank, agency, account }` válidos envia
      `PUT /central/operacoes-portabilidade/{protocolo}/conta-recebimento` assinado, atualiza a conta local,
      anexa evento `CONTA_ATUALIZADA` e responde `200` com a operação atualizada.
- [ ] Banco, agência ou conta em branco respondem `400`; operação inexistente responde `404`.
- [ ] Operação `PENDING` ou `REJECTED` responde `409`, sem chamar a Central, sem alterar a conta e sem evento.
- [ ] Conta igual à atual responde `200` sem chamar a Central, sem gravar e sem anexar evento.
- [ ] Falha, timeout ou resposta inesperada da Central respondem `502`, sem alterar a conta local e sem
      anexar evento.
- [ ] A atualização altera somente os dados da conta: status, protocolo, motivo de rejeição, parcelas,
      credor, chave de idempotência e momento da solicitação permanecem intactos.
- [ ] Após a atualização, a interface exibe a nova conta e o erro da API é mostrado quando a chamada falha.

## Fora de escopo

- Alterar o credor cessionário, as parcelas ou qualquer outro campo da operação.
- Rejeição aleatória, falhas ocasionais e atrasos imprevisíveis da Central (F07).
- Exposição da trilha de auditoria na API/interface (F06); a F05 apenas acrescenta um evento à trilha.
- Histórico de contas anteriores, versionamento da conta ou aprovação em duas etapas.
- Processamento em segundo plano ou Notificações quando a Central aplicar a mudança.
