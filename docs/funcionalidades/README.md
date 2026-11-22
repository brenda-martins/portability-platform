# Funcionalidades — estrutura de planejamento

Aqui ficam o planejamento e a documentação das funcionalidades: o "issue tracker" local do projeto.
A lista e o status de cada funcionalidade são do [`../roadmap.md`](../roadmap.md); este arquivo descreve
apenas a estrutura e as convenções.

## Hierarquia

```
docs/funcionalidades/<NN>-<titulo>/
  prd.md                       ← requisitos de produto (skill /prd)
  spec.md                      ← como será construída e testada
  tarefas/
    <NN>-<titulo>/
      tarefa.md                ← Status no topo; parte entregável da funcionalidade
      plano.md                 ← plano aprovado e congelado (skill /plan-task)
      ...                      ← registros da tarefa, se necessário
```

- `<NN>` tem dois dígitos; títulos em kebab-case sem acento (`01-autorizar-beneficiario`).
- Tarefas numeradas a partir de `01`, na ordem de dependência.
- Fora da pasta, a tarefa é `F<NN>-T<NN>` (ex.: `F03-T02`).

## Donos da informação

| Informação | Dono |
| --- | --- |
| Lista e status das funcionalidades | `docs/roadmap.md` |
| Requisitos de produto de uma funcionalidade | `prd.md` |
| Decisões de implementação e teste | `spec.md` |
| Status da tarefa | campo `Status` no `tarefa.md` |
| Plano aprovado | `plano.md` (congelado depois da aprovação) |

## Regras

- O `prd.md` aponta para `docs/product-brief.md` em vez de repetir métricas e requisitos do produto.
- A `spec.md` segue `docs/hld.md`, `docs/lld.md` e o README, apontando para eles.
- Antes do código, cada tarefa ganha um `plano.md`, aprovado pelo usuário. Depois disso o plano fica
  congelado: só o usuário o descongela. Decisões que se mostrarem erradas são corrigidas na spec, no LLD
  ou numa tarefa nova — nunca reescrevendo o plano aprovado. Versionar o plano em Git exige pedido
  expresso do usuário; regras de commit em [`../../AGENTS.md`](../../AGENTS.md), seção "Convenções de Git".
- A pasta da tarefa recebe só a tarefa, o plano e registros da própria tarefa (roteiro de teste manual,
  medição, captura de validação visual), em Markdown ou imagem com nome kebab-case. Código, fixtures e
  configuração ficam no projeto a que pertencem (`backend/` ou `frontend/`).
- Convenções de código, regras de negócio e SDD: [`../../AGENTS.md`](../../AGENTS.md).
