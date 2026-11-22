# Tarefa — F02-T01 Consulta de agenda com verificação de autorização

**Status:** planejada

## Objetivo

Entregar a consulta da agenda do beneficiário autorizado como uma fatia vertical: verificação da
autorização, tradução da resposta da Central, endpoint `/api`, fixture da Central simulada e painel com
valor, data, convênio de origem e status.

## Entregas

- Regra de autorização aplicada antes da chamada à Central, com `403` para beneficiário sem autorização.
- `DiscountAgendaPort` implementado em `CentralClient`, com tradução do contrato em pt-BR e tratamento
  de respostas inesperadas.
- `GET /api/beneficiaries/{beneficiaryId}/agenda` implementado, devolvendo a lista completa sem filtro.
- `GET /central/beneficiarios/{beneficiaryId}/agenda` implementado com fixture determinística.
- Tabela do painel com a coluna "Convênio" e testes dos comportamentos observáveis.

## Critérios de aceite

Os critérios de aceite são os do [`../../prd.md`](../../prd.md); os detalhes de implementação e teste
estão no [`../../spec.md`](../../spec.md) e o sequenciamento no [`plano.md`](plano.md).

## Fora desta tarefa

- Seleção/registro da operação (F03), confirmação assíncrona (F04), conta de recebimento (F05),
  auditoria (F06) e falhas aleatórias da Central (F07).
