# PRD — F02 Consultar e visualizar a agenda de descontos

> **Dono:** requisitos de produto desta funcionalidade. Escopo, fases e métricas em
> [`../../product-brief.md`](../../product-brief.md); termos em [`../../GLOSSARY.md`](../../GLOSSARY.md).

## Problema

Depois de autorizar o `opt-in` (F01), o beneficiário e o operador precisam ver quais descontos futuros
existem e a quem pertencem hoje. Sem essa visualização não há como decidir quais parcelas podem ser
portadas, nem como identificar que uma parcela já foi portada ou cancelada. A consulta só é legítima
quando o beneficiário autorizou o acesso.

## Fluxo do usuário

1. Com a autorização registrada, o usuário pede a agenda do beneficiário.
2. A plataforma verifica a autorização e consulta a agenda na Central de Convênios simulada.
3. A plataforma exibe todas as parcelas devolvidas pela Central, com valor, data de desconto, convênio
   de origem e status.
4. Parcelas abertas e com data de desconto futura aparecem disponíveis para seleção; as demais
   aparecem na lista, mas não podem ser selecionadas.
5. Sem autorização, ou diante de falha/resposta inesperada da Central, a plataforma exibe o motivo e
   não mostra parcelas como resultado da consulta.

## Histórias

- **Como beneficiário**, quero ver os descontos futuros autorizados para saber o que pode ser portado.
- **Como operador**, quero ver valor, data, convênio de origem e status de cada parcela para decidir a
  portabilidade.
- **Como usuário**, quero uma mensagem clara quando não estou autorizado ou quando a Central falha.

## Critérios de aceite

- [ ] `GET /api/beneficiaries/{beneficiaryId}/agenda` com autorização existente responde `200` com a
      lista de parcelas no formato do contrato `/api`.
- [ ] Cada parcela exibe `installmentId`, `agreementId`, `amount`, `discountDate` e `status`; o painel
      mostra valor, data, convênio e status, e desabilita a seleção de parcelas que não estejam `OPEN`
      com data futura.
- [ ] Beneficiário sem autorização responde `403` com `{ "message": "..." }`, sem chamar a Central.
- [ ] `beneficiaryId` em branco responde `400` com `{ "message": "..." }`, sem chamar a Central.
- [ ] Falha de comunicação, timeout ou resposta inesperada da Central responde `502` com
      `{ "message": "..." }`.
- [ ] A Central simulada responde `200` com uma lista determinística para `BEN-123` e uma lista vazia
      para identificadores desconhecidos.
- [ ] O painel exibe o estado vazio quando a consulta não retorna parcelas e mostra a mensagem de erro
      devolvida pela API quando a consulta falha.

## Fora de escopo

- Seleção das parcelas e registro da operação (F03) e confirmação assíncrona (F04).
- Atualização da conta de recebimento (F05), trilha de auditoria (F06) e falhas aleatórias/rejeições
  da Central (F07).
- Filtrar parcelas por elegibilidade no backend: a API devolve a lista recebida da Central; a
  seleção é validada na F03.
- Alteração de autorização, autenticação de usuários e contrato de agenda com assinatura na Central.
