# Design System — Portability Platform

> **Dono:** linguagem visual da interface — tokens, componentes, estados e comportamento responsivo.
> Leia este documento **antes** de qualquer tarefa de design ou interface. Termos em
> [`../GLOSSARY.md`](../GLOSSARY.md); estrutura do frontend no [`../frontend/AGENTS.md`](../frontend/AGENTS.md).

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
| Cor | `--color-accent-surface` | `#eff6ff` | Fundo sutil de etapas, badges pendentes e ações secundárias |
| Cor | `--color-danger` / `--color-danger-surface` | `#b91c1c` / `#fef2f2` | Alertas e rejeição |
| Cor | `--color-success` / `--color-success-surface` | `#166534` / `#f0fdf4` | Status e confirmação |
| Tipografia | `font-family` | `system-ui, -apple-system, 'Segoe UI', sans-serif` | Toda a interface |
| Tipografia | `title` | `1.1rem` | Título dos painéis (`h2`) |
| Espaçamento | `--space-xs` / `--space-sm` / `--space-md` / `--space-lg` | `0.25rem` / `1rem` / `1.25rem` / `2rem` | Margens e paddings |
| Raio | `--radius` / `--radius-sm` | `8px` / `6px` | Cantos dos painéis e de campos, botões e alertas |
| Sombra | — | nenhuma | Visual limpo, sem elevação |

## Componentes

| Componente | Classe | Uso |
| --- | --- | --- |
| Container da aplicação | `.app` | Limita a largura a `900px` e centraliza o conteúdo |
| Cabeçalho | `.app-header` / `.app-eyebrow` | Título, descrição e etiqueta do produto |
| Grade principal | `.app-main` | Empilha os quatro painéis com gap de `1rem` |
| Painel | `.panel` | Uma etapa do fluxo; fundo claro, borda e raio |
| Cabeçalho do painel | `.panel-header` / `.step` / `.panel-description` | Número da etapa, título e descrição curta |
| Grade de campos | `.field-grid` | Duas colunas em telas largas, uma em telas estreitas |
| Campo de formulário | `.field` | `label` associado ao `input` |
| Botão | `.button` / `.button--secondary` | Ações principais e secundárias, com `type` explícito e `disabled` real |
| Ações do formulário | `.form-actions` | Alinha botões e texto de carregamento |
| Dica / estado vazio | `.hint` / `.hint--block` | Texto secundário, carregamento e estados vazios explicativos |
| Tabela de parcelas | `.installments` | Seleção de parcelas elegíveis, com valor alinhado à direita |
| Resumo da operação | `.operation-summary` / `.summary-row` | Identificador, status, protocolo e conta da operação |
| Badge de status | `.status` / `.status--pending` / `.status--confirmed` / `.status--rejected` | Status da operação e confirmação de autorização |
| Alerta | `.alert` | Rejeição e falhas de API, com `role="alert"` |
| Lista de eventos | `.event-list` / `.event-type` / `.event-time` | Trilha de auditoria ordenada por data, com badge e horário |

## Estados de interação

- **Padrão:** borda e texto conforme tokens acima.
- **Hover:** leve escurecimento do botão principal; botão secundário ganha fundo sutil, linhas da
  tabela recebem o fundo da página e a borda do campo aproxima-se do acento.
- **Focus:** contorno visível (`:focus-visible`) obrigatório em inputs e botões; nunca remover sem substituto.
- **Desabilitado:** opacidade reduzida e `disabled` real no elemento, não apenas aparência.
- **Carregamento:** desabilitar a ação e exibir "Carregando…" junto ao botão/painel.
- **Erro:** mensagem legível no painel afetado, com o motivo da falha; nunca apenas `console.error`.
- **Vazio:** texto explicativo quando a agenda ou a trilha não tiver itens.

## Responsividade

- Layout: largura máxima de `900px`, padding `2rem 1rem 4rem`, grade com `gap: 1rem`.
- Em telas estreitas, manter o padding lateral de `1rem` e empilhar os painéis pela grade.
- Abaixo de `600px`, a tabela de parcelas rola horizontalmente, `.field-grid` vira uma coluna e as
  linhas do resumo da operação e o horário dos eventos são empilhados.