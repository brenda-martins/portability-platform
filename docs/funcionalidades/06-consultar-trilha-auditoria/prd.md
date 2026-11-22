# PRD — F06 Consultar a trilha de auditoria de cada operação

> **Dono:** requisitos de produto desta funcionalidade. Escopo, fases e métricas em
> [`../../product-brief.md`](../../product-brief.md); termos em [`../../GLOSSARY.md`](../../GLOSSARY.md);
> status em [`../../roadmap.md`](../../roadmap.md). Spec de origem: PDF do desafio, disponível fora do
> repositório; ele pede o painel com a trilha de eventos por operação, sem fixar contrato HTTP.

## Problema

O registro (F03), a confirmação assíncrona (F04) e a correção da conta (F05) já gravam eventos imutáveis,
mas o operador e o auditor não conseguem lê-los: a leitura da API e do repositório ainda são stubs. Sem a
trilha consultável, não há como conferir quando a operação foi solicitada, quando mudou de status ou qual
foi o retorno da Central — o histórico existe, mas não é apresentado.

## Fluxo do usuário

1. O operador informa o ID da operação ou deixa o ID preenchido automaticamente após registrar uma
   operação.
2. Aciona "Consultar auditoria".
3. A plataforma devolve os eventos da operação, ordenados do mais antigo para o mais recente, com tipo,
   mensagem e momento de cada um.
4. Se a operação não existir, o painel exibe a mensagem de erro; se existir sem eventos, exibe um estado
   vazio.
5. A consulta não acrescenta eventos, não altera a operação e não chama a Central.
6. Ao consultar outro ID, a trilha anterior é descartada para que eventos de operações diferentes não
   apareçam juntos.

## Histórias

- **Como operador**, quero consultar a trilha pelo ID da operação para conferir o que aconteceu sem
  refazer o fluxo.
- **Como auditor**, quero ver os eventos na ordem em que ocorreram, com tipo e mensagem legíveis, para
  reconstruir a história da operação.
- **Como auditor**, quero que a consulta seja somente leitura, para que histórico nenhum seja apagado ou
  sobrescrito.
- **Como operador**, quero ver o erro quando informo um ID inexistente, em vez de uma trilha vazia sem
  explicação.

## Critérios de aceite

- [ ] `GET /api/portability/{operationId}/audit` com operação existente responde `200` com uma lista de
      eventos contendo `eventId`, `operationId`, `eventType`, `message` e `occurredAt`.
- [ ] Os eventos são devolvidos em ordem crescente de `occurredAt` e apenas os da operação informada.
- [ ] Operação inexistente responde `404`, sem gravar eventos.
- [ ] Operação existente sem eventos responde `200` com uma lista vazia.
- [ ] A consulta não chama a Central, não altera a operação e não anexa eventos.
- [ ] O painel exibe os eventos consultados com tipo e mensagem, o estado vazio quando não há eventos e a
      mensagem de erro da API quando a consulta falha.
- [ ] O painel usa o ID da operação registrada quando há uma operação na tela e limpa a trilha anterior
      antes de cada nova consulta.

## Fora de escopo

- Exposição da trilha em CSV, paginação, filtro por tipo ou período e download.
- Histórico de autorizações ou de parcelas fora da operação consultada.
- Geração de novos eventos, edição ou remoção de eventos existentes.
- Rejeições e falhas ocasionais da Central (F07).
- Polling automático, notificações ou atualização em tempo real da trilha.
