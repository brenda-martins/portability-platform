# Design System — Portability Platform

> **Dono:** linguagem visual da interface — tokens, componentes, estados e comportamento responsivo.
> Leia este documento **antes** de qualquer tarefa de design ou interface. Termos em
> [`../GLOSSARY.md`](../GLOSSARY.md); estrutura do frontend no [`../frontend/AGENTS.md`](../frontend/AGENTS.md).

## Situação atual

Interface simplificada: uma única tela, CSS puro, sem biblioteca de componentes e sem Figma. Os quatro
painéis estão implementados em `frontend/src/features/` e estilizados em `frontend/src/index.css`.
Falhas da API são exibidas no painel afetado; não há "sucesso falso".

## Tokens

Tokens como custom properties em `:root`, em `frontend/src/index.css`:

| Categoria | Token | Valor | Uso |
| --- | --- | --- | --- |
| Cor | `--color-text` | `#1f2937` | Texto principal |
| Cor | `--color-surface` | `#ffffff` | Fundo dos painéis e texto sobre botões |
| Cor | `--color-canvas` | `#f8fafc` | Fundo da página |
| Cor | `--color-muted` | `#64748b` | Subtítulos, dicas e textos secundários |
| Cor | `--color-border` | `#e2e8f0` | Bordas de painéis, campos e tabelas |
| Cor | `--color-accent` | `#2563eb` | Botões e contorno de foco |
| Cor | `--color-danger` / `--color-danger-surface` | `#b91c1c` / `#fef2f2` | Alertas e rejeição |
| Cor | `--color-success` / `--color-success-surface` | `#166534` / `#f0fdf4` | Status e confirmação |
| Tipografia | `font-family` | `system-ui, -apple-system, 'Segoe UI', sans-serif` | Toda a interface |
| Tipografia | `title` | `1.1rem` | Título dos painéis (`h2`) |
| Espaçamento | `--space-xs` / `--space-sm` / `--space-md` / `--space-lg` | `0.25rem` / `1rem` / `1.25rem` / `2rem` | Margens e paddings |
| Raio | `--radius` | `8px` | Cantos dos painéis |
| Sombra | — | nenhuma | Visual limpo, sem elevação |

## Componentes

| Componente | Classe | Estado | Uso |
| --- | --- | --- | --- |
| Container da aplicação | `.app` | Implementado | Limita a largura a `900px` e centraliza o conteúdo |
| Cabeçalho | `.app-header` | Implementado | Título e descrição do produto |
| Grade principal | `.app-main` | Implementado | Empilha os quatro painéis com gap de `1rem` |
| Painel | `.panel` | Implementado | Uma etapa do fluxo; fundo claro, borda e raio |
| Campo de formulário | `.field` | Implementado | `label` associado ao `input` |
| Botão | `.button` | Implementado | Ações com `type` explícito e `disabled` real |
| Ações do formulário | `.form-actions` | Implementado | Alinha botões e texto de carregamento |
| Dica / estado vazio | `.hint` | Implementado | Texto secundário, carregamento e listas vazias |
| Tabela de parcelas | `.installments` | Implementado | Seleção de parcelas elegíveis |
| Badge de status | `.status` / `.status--rejected` | Implementado | Status da operação e confirmação de autorização |
| Alerta | `.alert` | Implementado | Rejeição e falhas de API, com `role="alert"` |
| Lista de eventos | `.event-list` / `.event-type` | Implementado | Trilha de auditoria ordenada por data |

## Estados de interação

- **Padrão:** borda e texto conforme tokens acima.
- **Hover:** leve escurecimento do botão; as bordas dos demais elementos permanecem estáveis.
- **Focus:** contorno visível (`:focus-visible`) obrigatório em inputs e botões; nunca remover sem substituto.
- **Desabilitado:** opacidade reduzida e `disabled` real no elemento, não apenas aparência.
- **Carregamento:** desabilitar a ação e exibir "Carregando…" junto ao botão/painel.
- **Erro:** mensagem legível no painel afetado, com o motivo da falha; nunca apenas `console.error`.
- **Vazio:** texto explicativo quando a agenda ou a trilha não tiver itens.

## Responsividade

- Layout: largura máxima de `900px`, padding `2rem 1rem 4rem`, grade com `gap: 1rem`.
- Em telas estreitas, manter o padding lateral de `1rem` e empilhar os painéis (comportamento já
  obtido pela grade).
- Abaixo de `600px`, a tabela de parcelas rola horizontalmente.

## Fluxo de trabalho com Figma

Não há arquivo do Figma nesta etapa. Se o projeto adotar Figma:

1. Registre o link do arquivo nesta seção.
2. Mantenha tokens do Figma e do CSS sincronizados; qualquer mudança de token atualiza este documento.
3. Ao passar do Figma para o código, use os componentes e tokens aqui listados — não valores avulsos.
4. Valide visualmente a tela implementada contra o Figma antes de concluir a tarefa.
5. Se uma skill de design estiver disponível, carregue-a; em divergência, este arquivo e o
   [`../AGENTS.md`](../AGENTS.md) prevalecem.
