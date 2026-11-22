# PRD — F07 Tratar e exibir rejeições e falhas de integração com a Central

> **Dono:** requisitos de produto desta funcionalidade. Escopo, fases e métricas em
> [`../../product-brief.md`](../../product-brief.md); termos em [`../../GLOSSARY.md`](../../GLOSSARY.md);
> status em [`../../roadmap.md`](../../roadmap.md). Spec de origem: PDF do desafio, disponível fora do
> repositório; ele pede a simulação de rejeição e falhas ocasionais e o tratamento visível dos dois cenários,
> sem fixar o contrato da Central.

## Problema

A Central de Convênios pode responder que não aceita a portabilidade ou pode ficar indisponível enquanto a
confirmação assíncrona é acompanhada. Sem uma forma repetível de produzir esses desfechos, o operador não
consegue demonstrar nem verificar o comportamento da plataforma; sem registro da falha, a trilha mostra uma
operação pendente, mas não explica por que a consulta não concluiu.

## Fluxo do usuário

1. O operador informa o credor cessionário. Para demonstrar os cenários, usa um marcador no campo:
   `REJEITAR` ou `FALHA` (ver spec e README para as palavras exatas). Sem marcador, o fluxo termina em
   confirmação.
2. Registra a operação e vê o protocolo com status `PENDING`.
3. Aciona "Consultar status":
   - **Rejeição:** a operação passa a `REJECTED`, o motivo volta da Central e aparece no painel; a trilha
     registra o evento `REJEITADA`.
   - **Falha de integração:** a consulta responde `502` com mensagem de erro, a operação permanece
     `PENDING` e a trilha registra um evento `FALHA_INTEGRACAO`, sem alterar o status.
4. Se a operação estiver confirmada, uma falha ao atualizar a conta também responde `502`, mantém a conta
   atual e registra `FALHA_INTEGRACAO`.
5. O operador consulta a trilha e vê eventos de sucesso, rejeição e falha na ordem em que ocorreram.

## Histórias

- **Como operador**, quero ver o status `REJECTED` e o motivo devolvido pela Central para entender por que a
  portabilidade não foi concluída.
- **Como operador**, quero receber uma mensagem clara quando a Central não responde, em vez de ver a tela
  parada sem explicação.
- **Como auditor**, quero que cada falha de integração relacionada a uma operação apareça como evento
  imutável na trilha, mesmo sem mudança de status.
- **Como avaliador**, quero reproduzir rejeição e falha de forma determinística, sem aleatoriedade nem
  reinício do backend.

## Critérios de aceite

- [ ] O mock da Central devolve `REJEITADA` com motivo quando o credor contém `REJEITAR` e devolve erro
      `503` quando contém `FALHA`; a comparação ignora maiúsculas/minúsculas e vale por protocolo.
- [ ] Sem marcador, o mock continua confirmando a partir da terceira consulta, como antes.
- [ ] Rejeição persiste `REJECTED` com o motivo, anexa evento `REJEITADA` e a UI exibe status e motivo.
- [ ] Falha de integração responde `502` com `{ "message": "..." }`, não altera status, conta nem motivo e
      anexa evento `FALHA_INTEGRACAO` com o ID da operação.
- [ ] Falha ao atualizar a conta também anexa `FALHA_INTEGRACAO` e mantém a conta anterior.
- [ ] Falha antes de existir operação (opt-in, agenda ou registro) não cria evento órfão: não há operação à
      qual anexá-lo e a resposta continua sendo `502`.
- [ ] A UI mostra a mensagem de erro da falha e o evento aparece na trilha consultada por F06.
- [ ] Nenhum endpoint, DTO, tabela ou dependência nova.

## Fora de escopo

- Falha aleatória ou configurável por variável de ambiente; o gatilho é o marcador no credor.
- Espera real de timeout no mock: a indisponibilidade é simulada por `503`; o cliente já tem read timeout de
  2 segundos para timeouts reais.
- Novas tentativas automáticas além da política de backoff de F04; retry fica com o operador.
- Alteração de status ou limpeza da trilha quando a integração falha.
- Configuração do mock por endpoint administrativo ou interface própria.
