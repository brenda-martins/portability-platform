# Design System — Portability Platform

> **Dono:** linguagem visual da interface — tokens, componentes, estados e comportamento responsivo.
> Leia este documento **antes** de qualquer tarefa de design ou interface. Termos em
> [`../GLOSSARY.md`](../GLOSSARY.md); estrutura do frontend no [`../frontend/AGENTS.md`](../frontend/AGENTS.md).

## Situação atual

A interface é simplificada: uma única tela, CSS puro, sem biblioteca de componentes e sem Figma nesta
etapa. Os estilos hoje moram em `frontend/src/index.css`. Este documento registra o que já existe e o que
deve ser introduzido quando os painéis deixarem de ser placeholders.

## Tokens

Os valores abaixo são os que já estão aplicados no CSS. A evolução prevista é movê-los para custom
properties em `:root` (`--color-*`, `--space-*`, `--radius-*`) para evitar valores repetidos.

| Categoria | Token | Valor | Uso |
| --- | --- | --- | --- |
| Cor | `text` | `#1f2937` | Texto principal |
| Cor | `surface` | `#ffffff` | Fundo dos painéis |
| Cor | `canvas` | `#f8fafc` | Fundo da página |
| Cor | `muted` | `#64748b` | Subtítulos e textos secundários |
| Cor | `placeholder` | `#94a3b8` | Texto de estado "a implementar" |
| Cor | `border` | `#e2e8f0` | Bordas dos painéis |
| Tipografia | `font-family` | `system-ui, -apple-system, 'Segoe UI', sans-serif` | Toda a interface |
| Tipografia | `title` | `1.1rem` | Título dos painéis (`h2`) |
| Espaçamento | `xs` / `sm` / `md` / `lg` | `0.25rem` / `1rem` / `1.25–1.5rem` / `2rem` | Margens e paddings |
| Raio | `radius` | `8px` | Cantos dos painéis |
| Sombra | — | nenhuma | Visual limpo, sem elevação |

## Componentes

| Componente | Classe | Estado atual | Uso previsto |
| --- | --- | --- | --- |
| Container da aplicação | `.app` | Implementado | Limita a largura a `900px` e centraliza o conteúdo |
| Cabeçalho | `.app-header` | Implementado | Título e descrição do produto |
| Grade principal | `.app-main` | Implementado | Empilha os quatro painéis com gap de `1rem` |
| Painel | `.panel` | Implementado | Uma etapa do fluxo; fundo claro, borda e raio |
| Placeholder | `.placeholder` | Implementado | Texto itálico sinalizando funcionalidade pendente |
| Botão | `.button` | A criar | Ações com `type` explícito e estado de carregamento |
| Campo de formulário | `.field` | A criar | `label` associado, mensagem de erro e ajuda |
| Tabela de parcelas | `.installments` | A criar | Seleção de parcelas elegíveis |
| Badge de status | `.status` | A criar | `PENDING` / `CONFIRMED` / `REJECTED` com cor e texto |
| Alerta | `.alert` | A criar | Rejeição e falhas de integração |

## Estados de interação

- **Padrão:** borda e texto conforme tokens acima.
- **Hover:** leve escurecimento da borda em elementos clicáveis.
- **Focus:** contorno visível (`:focus-visible`) obrigatório em inputs e botões; nunca remover sem substituto.
- **Desabilitado:** opacidade reduzida e `disabled` real no elemento, não apenas aparência.
- **Carregamento:** desabilitar a ação e exibir texto ("Carregando…") junto ao botão/painel.
- **Erro:** mensagem legível no painel afetado, com o motivo da falha; nunca apenas `console.error`.
- **Vazio:** texto explicativo quando a agenda ou a trilha não tiver itens.

## Responsividade

- Layout atual: largura máxima de `900px`, padding `2rem 1rem 4rem`, grade com `gap: 1rem`.
- Em telas estreitas, manter o padding lateral de `1rem` e empilhar os painéis (comportamento já
  obtido pela grade).
- Pontos de quebra, tabelas roláveis e ajustes de fonte serão definidos quando houver conteúdo real.

## Fluxo de trabalho com Figma

Não há arquivo do Figma nesta etapa. Se o projeto adotar Figma:

1. Registre o link do arquivo nesta seção.
2. Mantenha tokens do Figma e do CSS sincronizados; qualquer mudança de token atualiza este documento.
3. Ao passar do Figma para o código, use os componentes e tokens aqui listados — não valores avulsos.
4. Valide visualmente a tela implementada contra o Figma antes de concluir a tarefa.
5. Se uma skill de design estiver disponível, carregue-a; em divergência, este arquivo e o
   [`../AGENTS.md`](../AGENTS.md) prevalecem.
