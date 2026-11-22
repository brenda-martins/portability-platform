# PRD — F01 Autorizar o beneficiário (opt-in)

> **Dono:** requisitos de produto desta funcionalidade. Escopo, fases e métricas em
> [`../../product-brief.md`](../../product-brief.md); termos em [`../../GLOSSARY.md`](../../GLOSSARY.md).

## Problema

O beneficiário precisa autorizar a consulta da própria agenda antes de qualquer desconto ser portado.
Sem esse consentimento vinculado a um convênio, a operadora não sabe se pode buscar as parcelas na
Central de Convênios. O opt-in também precisa ser confiável: um reenvio por duplo clique ou falha de rede
não deve criar duas autorizações para o mesmo par beneficiário/convênio.

## Fluxo do usuário

1. O beneficiário informa seu identificador e o identificador do convênio de origem.
2. A plataforma registra a autorização na Central de Convênios simulada e a guarda localmente.
3. A plataforma confirma o identificador da autorização ao usuário.
4. Se o mesmo par já estiver autorizado, a plataforma mostra a autorização existente, sem criar outra.
5. Se a Central estiver indisponível ou rejeitar a mensagem, a plataforma exibe o erro e não registra a
   autorização.

## Histórias

- **Como beneficiário**, quero autorizar a consulta da minha agenda para que a operadora possa me
  apresentar as parcelas elegíveis.
- **Como operador**, quero ver o identificador da autorização para saber que o consentimento foi
  registrado e associá-lo à consulta da agenda.
- **Como usuário**, quero que um reenvio do formulário não gere autorizações duplicadas.

## Critérios de aceite

- [ ] `POST /api/authorizations` com `beneficiaryId` e `agreementId` preenchidos registra a autorização
      na Central e localmente, e responde `201` com `authorizationId`, `beneficiaryId`, `agreementId` e
      `createdAt`.
- [ ] Identificador e convênio ausentes ou em branco são rejeitados com `400`, sem chamada à Central.
- [ ] Reenviar o mesmo `beneficiaryId` + `agreementId` responde `200` com a autorização existente, sem
      criar outra e sem chamar a Central novamente.
- [ ] Falha de comunicação com a Central, timeout ou assinatura inválida responde `502` e não persiste
      autorização local.
- [ ] O painel de autorização exibe a confirmação com o identificador recebido e mostra a mensagem de
      erro devolvida pela API quando a solicitação falha.

## Fora de escopo

- Consulta da agenda em si (F02) e registro da operação de portabilidade (F03).
- Rejeição aleatória, atraso de confirmação e retry/backoff da Central (F04 e F07).
- Autenticação do beneficiário, assinatura digital real e alteração/remoção de autorização.
