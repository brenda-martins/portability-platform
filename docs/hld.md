# HLD — High-level design

> **Dono:** visão geral do sistema, responsabilidades por camada e fluxos principais. Versões das
> tecnologias e contratos HTTP ficam no README, seções "Tecnologias" e "Documentação da API";
> detalhes de implementação em [`lld.md`](lld.md);
> convenções de código no [`../AGENTS.md`](../AGENTS.md); termos em [`../GLOSSARY.md`](../GLOSSARY.md).
> Quando uma tarefa alterar algo registrado aqui, carregue a skill `hld` (se disponível) em modo
> atualizar antes de editar este arquivo.

## Contexto do sistema

```mermaid
flowchart LR
    Beneficiario["Beneficiário / Operador"]
    UI["Frontend (React + TS)<br/>tela única"]
    API["Backend (Spring Boot)<br/>API /api"]
    Domain["Domínio + aplicação<br/>ports e casos de uso"]
    Adapters["Adapters de saída<br/>JPA e integração"]
    DB[("PostgreSQL<br/>Docker Compose")]
    Central["Central de Convênios<br/>mock em /central"]

    Beneficiario --> UI
    UI -->|"HTTP /api"| API
    API --> Domain
    Domain --> Adapters
    Adapters --> DB
    Adapters -->|"HTTP assinado"| Central
```

## Responsabilidades por camada

| Camada | Responsabilidade | Não faz |
| --- | --- | --- |
| Frontend (`frontend/`) | Apresentar os quatro painéis, coletar entradas, selecionar parcelas, exibir status/auditoria e erros | Regras de negócio, acesso direto a banco/API fora do `client.ts` |
| `adapters/in/web` | Receber HTTP, validar entrada e traduzir para casos de uso | Regras de domínio, acesso a repositórios |
| `application` | Orquestrar casos de uso usando ports | Conhecer HTTP, JPA ou a Central |
| `domain` | Modelo, invariantes e contratos (ports) | Depender de framework |
| `adapters/out/persistence` | Persistir em PostgreSQL via JPA e mapear para o domínio | Definir regras de negócio |
| `adapters/out/central` | Falar com a Central, assinar/verificar e traduzir o contrato | Expor tipos da Central ao domínio |

A regra de dependência é sempre **para o domínio**. A Central simulada é um adapter interno, não um
terceiro projeto.

## Fluxo 1 — autorização e agenda

```mermaid
sequenceDiagram
    participant U as Beneficiário
    participant F as Frontend
    participant A as API /api
    participant C as Central /central

    U->>F: informa beneficiário e convênio
    F->>A: POST /api/authorizations
    A->>C: POST /central/autorizacoes (HMAC)
    C-->>A: 201 com authorizationId
    A-->>F: autorização registrada
    U->>F: pede agenda
    F->>A: GET /api/beneficiaries/{id}/agenda
    A->>A: verifica autorização
    A->>C: GET /central/beneficiarios/{id}/agenda
    C-->>A: lista completa de parcelas
    A-->>F: parcelas da agenda
```

A seleção de parcelas elegíveis acontece no registro (F03), não nesta consulta; detalhes em
[`lld.md`](lld.md), seção "Integração, idempotência e erros".

## Fluxo 2 — registro e confirmação assíncrona

```mermaid
sequenceDiagram
    participant F as Frontend
    participant A as API /api
    participant C as Central /central

    F->>A: POST /api/portability (parcelas + conta)
    A->>A: valida idempotência e elegibilidade
    alt reenvio da mesma solicitação
        A-->>F: 200 com operação existente
    else solicitação nova
        A->>C: POST /central/operacoes-portabilidade (HMAC)
        C-->>A: 202 com protocolo
        A->>A: grava operação PENDING e evento "solicitada"
        A-->>F: 201 com operação
    end
    F->>A: GET /api/portability/{operationId}
    A->>C: GET /central/operacoes-portabilidade/{protocol} (Retry-After)
    alt confirmada
        C-->>A: 200 CONFIRMADA
        A->>A: atualiza status e anexa evento CONFIRMADA
        A-->>F: 200 com a operação confirmada
    else pendente
        C-->>A: 204 com Retry-After
        A->>A: aguarda o backoff e tenta de novo (até 3 vezes)
        A-->>F: 200 com a operação ainda PENDING
    else rejeitada
        C-->>A: 200 REJEITADA com motivo
        A->>A: grava REJECTED e anexa evento REJEITADA
        A-->>F: 200 com status e motivo da rejeição
    else falha de integração
        C-->>A: 503 ou timeout
        A->>A: anexa evento FALHA_INTEGRACAO sem alterar o status
        A-->>F: 502 com mensagem de erro
    end
    Note over F,A: F05: com a operação CONFIRMED, o operador corrige a conta de recebimento
    F->>A: PUT /api/portability/{operationId}/receiving-account
    A->>C: PUT /central/operacoes-portabilidade/{protocol} (HMAC)
    C-->>A: 200 com a conta aceita
    A->>A: atualiza conta local e anexa evento CONTA_ATUALIZADA
    A-->>F: 200 com a operação atualizada
    Note over C: F07: o mock escolhe o desfecho por marcador no credor (detalhes no LLD)
```

## Fluxo 3 — trilha de auditoria

```mermaid
sequenceDiagram
    participant F as Frontend
    participant A as API /api

    F->>A: GET /api/portability/{operationId}/audit
    A->>A: verifica a operação e lê os eventos append-only
    A-->>F: 200 com eventos ordenados (ou 404 se inexistente)
```

A consulta é somente leitura: não chama a Central, não acrescenta eventos e não altera a operação.

## Documentos relacionados

- Versões das tecnologias: README, seção "Tecnologias".
- Contratos HTTP: README, seção "Documentação da API".
- Sequência detalhada, mapeamentos e estruturas internas: [`lld.md`](lld.md).
- Funcionalidades e status: [`roadmap.md`](roadmap.md).
