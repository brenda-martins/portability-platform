# Funcionalidades — estrutura de planejamento

Aqui ficam o planejamento e a documentação das funcionalidades: o "issue tracker" local do projeto.
Este arquivo descreve a estrutura e as convenções de PRD e specs.

## Hierarquia

```
docs/funcionalidades/<NN>-<titulo>/
  prd.md                       ← requisitos de produto
  spec.md                      ← como será construída e testada
```

- `<NN>` tem dois dígitos; títulos em kebab-case sem acento (`01-autorizar-beneficiario`).

## Donos da informação

| Informação | Dono |
| --- | --- |
| Requisitos de produto de uma funcionalidade | `prd.md` |
| Decisões de implementação e teste | `spec.md` |

## Regras

- O `prd.md` aponta para `docs/product-brief.md` em vez de repetir métricas e requisitos do produto.
- A `spec.md` segue `docs/hld.md`, `docs/lld.md` e o README, apontando para eles.