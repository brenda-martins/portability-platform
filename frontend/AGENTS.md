# AGENTS.md — frontend

Projeto: `frontend/` — React 19 + TypeScript 5.9 + Vite 6, interface simplificada de tela única.

## Documentos de referência

- Convenções, regras de negócio, Git e SDD: [`../AGENTS.md`](../AGENTS.md).
- Linguagem visual (tokens, componentes, estados): [`../docs/design-system.md`](../docs/design-system.md).
  **Leia antes de qualquer tarefa de interface.**
- Vocabulário: [`../GLOSSARY.md`](../GLOSSARY.md).
- Contratos e execução: [`../README.md`](../README.md).

## Stack e comandos

```bash
npm ci          # uma vez, ou após mudar package-lock.json
npm run dev     # http://localhost:5173 (proxy /api -> :8080)
npm test        # Vitest + Testing Library
npm run build   # tsc --noEmit && vite build
npm run preview # serve o build de dist/
```

Não há Docker neste projeto; rode npm diretamente na máquina.

## Práticas específicas

- Componentes funcionais com `export function Nome()`; só `App` usa `export default`.
- Uma feature por pasta em `src/features/`. Nenhum componente chama `fetch` diretamente: use
  `api/client.ts`. DTOs ficam em `src/types/index.ts`.
- Sem router, biblioteca de estado global ou UI kit sem aprovação explícita.
- Estilos em `index.css` com as classes do design system; sem CSS inline.
- Testes por papel/texto visível (`getByRole`, `getByText`), mock de `apiFetch`, nunca de `fetch`.
- Erros de API são exibidos ao usuário; estados de carregamento e vazio são explícitos.

## Verificação completa

Rodar antes de abrir um PR de uma tarefa que tocou o frontend:

```bash
(cd frontend && npm test && npm run build)
```

Se a tarefa criar uma nova suíte de testes ou comando de verificação, acrescente-o aqui e na seção
"Verificação completa" do [`../AGENTS.md`](../AGENTS.md).
