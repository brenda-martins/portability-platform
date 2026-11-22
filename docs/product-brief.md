# Product Brief — Portability Platform

> **Dono:** escopo do produto, público, fases e métricas. Detalhes de arquitetura ficam em
> [`hld.md`](hld.md), de implementação em [`lld.md`](lld.md) e o vocabulário em
> [`../GLOSSARY.md`](../GLOSSARY.md). Spec imutável: PDF do desafio.

## O quê e para quem

Uma plataforma que permite a uma empresa atuar como **nova credora** de convênios de desconto em folha,
assumindo as parcelas hoje devidas a outro credor. É usada por:

- **Beneficiário:** dá o `opt-in` para consulta da própria agenda e acompanha a portabilidade.
- **Operador da credora:** seleciona parcelas elegíveis, informa a conta de recebimento e acompanha
  confirmações, rejeições e a trilha de auditoria.

## Problema

Hoje a portabilidade exige integração manual com a Central de Convênios: capturar autorização, buscar
agenda, registrar a operação e conferir a confirmação assíncrona. Sem uma plataforma, o processo é
repetitivo, sujeito a duplicidade de operações e sem trilha auditável.

## Proposta de valor

Um fluxo único, auditável e idempotente, com tratamento explícito de falhas da Central, que leva o
beneficiário do `opt-in` até a atualização da conta de recebimento.

## MVP — escopo

Os seis fluxos exigidos pelo PDF:

1. Registrar a autorização (`opt-in`) do beneficiário vinculada a um identificador único e a um convênio.
2. Consultar a agenda de descontos futuros de beneficiário autorizado (valor, data, convênio e status).
3. Selecionar uma ou mais parcelas elegíveis e iniciar a operação informando a conta de recebimento.
4. Acompanhar o status (`PENDING` / `CONFIRMED` / `REJECTED`) com confirmação assíncrona da Central.
5. Exibir, por operação, a trilha de eventos (solicitação, mudanças de status e retorno da Central).
6. Tratar e exibir rejeições e falhas de integração com a Central.

Requisitos não funcionais do MVP: HMAC simplificado, idempotência no registro, retry com backoff na
consulta de status, tratamento explícito de indisponibilidade/timeout/resposta inesperada, eventos de
auditoria append-only e testes dos cenários críticos.

**Estado atual:** o MVP segue sem regra de negócio implementada nos seis fluxos; a interface já
exercita autorização, agenda, registro, status e auditoria contra os endpoints atuais, que permanecem
como stubs. O status por funcionalidade está em [`roadmap.md`](roadmap.md).

## Fora de escopo

- Central de Convênios real, certificado digital ou assinatura criptográfica completa.
- Autenticação/autorização de usuários, multi-tenancy e gestão de permissões.
- Banco de dados de produção, migrações, observabilidade e CI/CD nesta etapa; o Compose local sobe
  apenas o PostgreSQL de desenvolvimento.
- Design visual elaborado, Figma, i18n e aplicativo mobile.
- Qualquer funcionalidade de negócio não listada nos seis fluxos acima.

## Planos (fases do produto)

1. **Scaffold (atual):** estrutura hexagonal, ports, DTOs, schema inicial, smoke tests e documentação.
2. **Fluxo core:** autorização, agenda, seleção de parcelas e registro da operação com protocolo.
3. **Robustez:** confirmação assíncrona com backoff, idempotência, HMAC, auditoria e tratamento de erros.
4. **Entrega:** interface conectada, cenários críticos cobertos, README final e publicação do repositório.

## Métricas de sucesso

| Métrica | Meta |
| --- | --- |
| Operações duplicadas por reenvio | 0 (idempotência verificada por teste) |
| Mudanças de estado sem evento de auditoria | 0 |
| Falhas de integração exibidas ao usuário | 100% dos cenários de timeout/indisponibilidade/rejeição |
| Cenários críticos do PDF cobertos por teste automatizado | idempotência, falha de integração e transições de estado |
| Tempo até a confirmação | independente do cliente; acompanhado por `Retry-After` com backoff |
