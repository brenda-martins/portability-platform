# Roadmap — Portability Platform

> **Dono:** lista de funcionalidades e seus status. O status de cada tarefa fica no campo `Status` do
> `tarefa.md` correspondente; a descrição técnica de cada funcionalidade fica em
> `docs/funcionalidades/<NN>-<titulo>/spec.md`. Termos conforme [`../GLOSSARY.md`](../GLOSSARY.md).

## Status

`planejada` → `PRD pronto` → `spec pronta` → `em andamento` → `concluída`

Uma funcionalidade passa a **em andamento** no commit de plano da primeira tarefa e a **concluída** quando a última tarefa é integrada. Documentos de funcionalidade só são criados quando a funcionalidade começa a ser trabalhada; a numeração é global, sequencial e não muda depois de atribuída.

## Funcionalidades

| ID | Funcionalidade | Status | Depende de |
| --- | --- | --- | --- |
| F00 | Scaffold do projeto (backend hexagonal + frontend simplificado + documentação) | concluída | — |
| F01 | Autorizar o beneficiário a consultar sua agenda (`opt-in`) | concluída | F00 |
| F02 | Consultar e visualizar a agenda de descontos futuros | concluída | F01 |
| F03 | Selecionar parcelas elegíveis e registrar a operação de portabilidade | planejada | F01, F02 |
| F04 | Acompanhar a confirmação assíncrona com retry e backoff | planejada | F03 |
| F05 | Atualizar a conta de recebimento após a confirmação | planejada | F04 |
| F06 | Consultar a trilha de auditoria de cada operação | planejada | F01, F03 |
| F07 | Tratar e exibir rejeições e falhas de integração com a Central | planejada | F03, F04 |
| F08 | PostgreSQL local via Compose e interface dos fluxos contra `/api` | concluída | F00 |

## Convenções de numeração

- Pastas: `docs/funcionalidades/<NN>-<titulo>/`, com `NN` de dois dígitos e título em kebab-case sem
  acento (ex.: `01-autorizar-beneficiario`).
- Tarefas: `docs/funcionalidades/<NN>-<titulo>/tarefas/<NN>-<titulo>/`, numeradas a partir de `01` na
  ordem de dependência.
- Fora da pasta da funcionalidade, uma tarefa é identificada como `F<NN>-T<NN>` (ex.: `F03-T02`).

## Próximas ações

1. Abrir o PRD de F01 (skill `/prd`, quando disponível) e em seguida a spec.
2. Derivar a primeira tarefa, escrever o plano e commitá-lo sozinho (commit de plano).
3. Executar a tarefa com SDD (ver [`../AGENTS.md`](../AGENTS.md), seção "Processo obrigatório de SDD").
4. Atualizar o status nesta tabela nos marcos descritos acima.
