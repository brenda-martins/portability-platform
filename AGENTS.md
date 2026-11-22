# AGENTS.md

> **Finalidade:** este é o arquivo de memória principal do repositório e a fonte de instruções para
> agentes de IA. Leia-o por completo **antes** de alterar código, contratos, planejamento ou documentação.
> **Idioma:** documentação em português (pt-BR); identificadores de código em inglês, conforme solicitado.

## Documentos

Em `docs/` ficam os documentos que definem o produto, a arquitetura e os detalhes de implementação:

1. `docs/product-brief.md`: o quê e para quem. Escopo do MVP, fora de escopo, fases e métricas.
2. `docs/hld.md`: *high-level design* — decisões de arquitetura, fluxos principais e visão do sistema.
3. `docs/lld.md`: *low-level design* — ports, mapeamentos, persistência, integração e tratamento de erros.
4. `docs/design-system.md`: linguagem visual — tokens, componentes, estados e responsividade.
5. `docs/roadmap.md`: funcionalidades, dependências e status.
6. `GLOSSARY.md`, na raiz: vocabulário do domínio. Use esses termos nos documentos e no planejamento.
7. `docs/funcionalidades/`: PRD, spec, tarefas e planos (estrutura em `docs/funcionalidades/README.md`).
8. `README.md`, na raiz: execução, contratos, modelo de dados e versões das tecnologias.
9. PDF do desafio (spec imutável) e Apêndice A deste arquivo (registro do uso de IA).

Cada projeto tem o próprio `AGENTS.md`, com os comandos, as particularidades da stack e as práticas de
código dele:

- `backend/AGENTS.md`: Java 21, Spring Boot 4.2.0, Maven, PostgreSQL (H2 nos testes).
- `frontend/AGENTS.md`: React 19, TypeScript, Vite 6, Vitest.

### Onde cada informação mora

| Informação | Dono |
| --- | --- |
| O que o sistema faz: escopo, fases, métricas | `docs/product-brief.md` |
| Arquitetura de alto nível e fluxos | `docs/hld.md` |
| Detalhes de implementação | `docs/lld.md` |
| Linguagem visual | `docs/design-system.md` |
| Lista e status das funcionalidades | `docs/roadmap.md` |
| Vocabulário do domínio | `GLOSSARY.md` |
| PRD, spec, tarefas e planos | `docs/funcionalidades/<NN>-<titulo>/` |
| Execução, API, esquema e versões das tecnologias | `README.md` |
| Convenções, regras de negócio, restrições e processo para agentes | este `AGENTS.md` |
| Comandos e práticas específicas | `backend/AGENTS.md`, `frontend/AGENTS.md` |
| Histórico do uso de IA | Apêndice A deste arquivo |

- **Nunca nos dois.** Uma informação tem um dono só; os outros arquivos apontam para ele pelo nome do
  documento e da seção, sem repetir o conteúdo. Resumos são permitidos apenas quando necessários para
  compreensão, e devem indicar o dono.
- **Documentar funcionalidades e contratos.** PRD, spec, design system e roadmap descrevem o que o
  sistema faz; README registra execução, API, modelo de dados e versões. Não registre histórico de
  ajustes técnicos, decisões tomadas durante a implementação nem exceções de planejamento.
- **HLD e LLD mudam pela skill deles.** Quando uma tarefa altera algo que `docs/hld.md` ou `docs/lld.md`
  registra, carregue a skill `hld` ou `lld` (se disponível) e use o modo atualizar antes de editar.
- Informações que deveriam estar em arquivos de memória (este `AGENTS.md` e os `AGENTS.md` de projeto)
  não devem ser repetidas nos documentos de arquitetura: faça ponteiros para eles.

## Contexto e objetivo

A plataforma simula a portabilidade de convênios de desconto em folha: uma empresa passa a ser a nova
credora de parcelas que hoje pertencem a outro credor. O fluxo completo envolve capturar o `opt-in` do
beneficiário, consultar sua agenda de descontos futuros na Central de Convênios, registrar a operação,
acompanhar a confirmação assíncrona e exibir a trilha de auditoria. O detalhamento está em
`docs/product-brief.md`.

> Não apresente stubs como funcionalidade pronta. Ao implementar algo, remova o stub correspondente e
> atualize o status em `docs/roadmap.md`.

## Especificações e ordem de precedência

Sempre consulte as especificações antes de implementar. Em caso de dúvida, **pergunte**; nunca resolva
uma ambiguidade silenciosamente.

1. **Requisitos do usuário:** registrados no Apêndice A.
2. **Documentos de produto e arquitetura:** `docs/product-brief.md`, `docs/hld.md`, `docs/lld.md`,
   `docs/design-system.md`, `docs/roadmap.md` e `GLOSSARY.md`.
3. **Este `AGENTS.md`** e os `AGENTS.md` de projeto: convenções, restrições e processo.
4. **`README.md`:** execução, contratos expostos e modelo de dados.

## Stack tecnológica

Versões: README, seção "Tecnologias". Como executar: README, seção "Execução".

## Arquitetura definida

**Arquitetura hexagonal (ports and adapters)** no backend; frontend simplificado, de tela única.

```
UI (React) --HTTP /api--> adapters/in/web --> domain/ports/in
                                                   ^
                                                   | implementa
                                          application (casos de uso)
                                                   |
                                                   v depende de
                                          domain/ports/out <-- adapters/out
                                          (persistência, Central, assinatura)
```

- O **domínio não depende de nada**: nem Spring, nem JPA, nem HTTP, nem banco de dados.
- `application` depende **apenas** de `domain` (modelo + ports de entrada e saída).
- `adapters/in` dependem dos ports de entrada; `adapters/out` dependem dos ports de saída.
- A Central mockada vive no backend, sob `/central`, seguindo o contrato do PDF. É um adapter, não um
  domínio, e não deve ser chamada diretamente por controllers da API.
- A conversão entre o contrato da Central (pt-BR) e o modelo interno (inglês) é responsabilidade de
  `adapters/out/central`.

Visão completa e fluxos: `docs/hld.md`. Detalhes: `docs/lld.md`. Contratos: `README.md`.

## Estrutura de diretórios

```text
portability-platform/
├── AGENTS.md                      # este arquivo (memória + registro do harness)
├── README.md                      # documentação pública do projeto
├── GLOSSARY.md                    # vocabulário do domínio
├── compose.yaml                   # PostgreSQL local para desenvolvimento
├── .gitignore
├── docs/
│   ├── product-brief.md
│   ├── hld.md
│   ├── lld.md
│   ├── design-system.md
│   ├── roadmap.md
│   └── funcionalidades/
│       ├── README.md              # convenções de PRD/spec/tarefa/plano
│       └── <NN>-<titulo>/         # criado quando a funcionalidade começar
│           ├── prd.md
│           ├── spec.md
│           └── tarefas/<NN>-<titulo>/{tarefa.md, plano.md, ...}
├── backend/
│   ├── AGENTS.md                  # comandos e práticas do backend
│   ├── pom.xml / README.md
│   └── src/
│       ├── main/java/com/portability/
│       │   ├── PortabilityApplication.java
│       │   ├── domain/
│       │   │   ├── model/          # records e enums (sem framework)
│       │   │   └── ports/{in,out}/ # casos de uso e integrações
│       │   ├── application/        # orquestração dos casos de uso
│       │   └── adapters/
│       │       ├── in/web/         # controllers REST (/api)
│       │       └── out/{persistence,central}/  # JPA, cliente e mock
│       ├── main/resources/application.yml
│       └── test/java/com/portability/
└── frontend/
    ├── AGENTS.md                  # comandos e práticas do frontend
    ├── package.json / package-lock.json / tsconfig.json / vite.config.ts
    ├── index.html / README.md
    └── src/
        ├── main.tsx / App.tsx / App.test.tsx / index.css / vite-env.d.ts
        ├── api/client.ts           # único wrapper fetch
        ├── components/             # layout compartilhado
        ├── features/               # authorization, agenda, portability, audit
        └── types/index.ts          # DTOs do contrato /api
```

`backend/` e `frontend/` são as únicas pastas de **código**; `docs/` contém somente documentação.

## Execução

Comandos, pré-requisitos e configuração: README, seção "Execução". Comandos e práticas específicos:
`backend/AGENTS.md` e `frontend/AGENTS.md`.

## Planejamento

O planejamento e a documentação das funcionalidades ficam em arquivos Markdown locais, que são o issue
tracker do projeto. As skills que publicam em tracker gravam nesses arquivos, sem rótulos de triagem.
A hierarquia é **funcionalidade → tarefa**, e cada funcionalidade tem dois documentos: PRD e spec.

- **Funcionalidade:** uma entrega demonstrável no produto, descrita só pelo que a pessoa consegue fazer,
  sem detalhes técnicos. Não é a Fase do `docs/product-brief.md`. O número é global, sequencial e não
  muda depois de atribuído. Na documentação de planejamento, "funcionalidade" tem sempre este sentido;
  para o sentido comum, use "recurso" ou "comportamento".
- **PRD:** requisitos de produto da funcionalidade (problema, fluxos, histórias, critérios de aceite,
  fora de escopo), feito pela skill `/prd` (quando disponível; caso contrário, siga o formato
  manualmente). Aponta para `docs/product-brief.md` em vez de repetir métricas e requisitos gerais.
- **Spec:** como a funcionalidade será construída (decisões de implementação e de teste). Segue
  `docs/hld.md`, `docs/lld.md` e o README, apontando para eles.
- **Tarefa:** parte entregável de uma funcionalidade, derivada da spec. É onde entra o trabalho técnico.
  Antes do código, cada tarefa ganha um **plano**, feito pela skill `/plan-task` (quando disponível),
  aprovado pelo usuário. Depois disso o plano fica congelado como registro do que foi aprovado: só o
  usuário o descongela. Uma decisão que se mostrar errada é corrigida na spec, no LLD ou numa tarefa
  nova. Fora da pasta da funcionalidade, a tarefa é `F<NN>-T<NN>`. Versionar o plano em Git só acontece
  se o usuário pedir expressamente, conforme "Convenções de Git".
- **Melhoria do roadmap:** implemente a melhoria solicitada consultando as specs existentes e atualize
  os documentos donos que forem afetados. Não crie PRD, spec, tarefa, plano ou nova funcionalidade só
  para executar uma melhoria; crie esses artefatos apenas quando o usuário pedir planejamento ou quando
  a solicitação iniciar uma nova funcionalidade. Resolva ambiguidades na conversa antes de implementar.

Estrutura, numeração e status: `docs/roadmap.md` e `docs/funcionalidades/README.md`.
O status da funcionalidade mora no roadmap; o da tarefa, no campo `Status` do `tarefa.md`.


## Diretrizes para criação de funcionalidades

- Trabalhe em **fatias verticais** de uma única tarefa por vez:
  modelo/port de domínio → caso de uso → adapters → endpoint → UI → testes → documentação.
- Antes de codificar, identifique a funcionalidade no roadmap e a spec que cobre a tarefa; se não houver
  PRD/spec, escreva-os e confirme com o usuário (ver SDD).
- Mantenha os stubs restantes honestos: não implementar "um pedacinho" de outra funcionalidade para
  fazer a atual parecer completa.
- Não crie endpoint, DTO ou tabela sem um caso de uso que o justifique. Não duplique responsabilidade
  entre ports, adapters, controllers e componentes.
- Valide entradas na borda e aplique invariantes no domínio; trate erros em todos os caminhos, inclusive
  falhas parciais da Central.
- Qualquer alteração de contrato (JSON, status HTTP, nome de porta, schema) exige atualização do README,
  de `docs/lld.md`, dos DTOs, dos testes e do registro de decisões. O PDF é imutável.
- Se a solicitação fugir do escopo atual, registre-a no roadmap e pare; não a implemente "de graça".

## Diretrizes para testes

- **Backend:** testes de domínio/aplicação devem ser unitários, com fakes dos ports (sem subir Spring).
  Use `@SpringBootTest` apenas para smoke tests e integração real de contexto; use `MockMvc` para
  contratos HTTP. A Central mockada pode ser exercitada em processo.
- Cenários críticos obrigatórios quando a funcionalidade existir: idempotência (reenvio), falha de
  integração (timeout/indisponibilidade/resposta inesperada), transições de estado e trilha de auditoria
  append-only, assinatura/HMAC e rejeição.
- **Frontend:** Vitest + Testing Library + jsdom. Teste comportamento observável (o que o usuário vê e
  faz). Mock `apiFetch`, não o `fetch` global. Um comportamento por teste, nomes descritivos no formato
  `deve ... quando ...`.
- Nunca desabilite, pule ou enfraqueca teste para deixar o build verde. Corrija a causa.
- Todo teste adicionado deve passar na Verificação completa (abaixo).

## Regras de documentação

- `README.md` é a porta de entrada pública e o dono de execução, API, esquema e versões das tecnologias.
  Atualize-o somente quando um desses conteúdos mudar — não para registrar cada alteração de código.
- `docs/` é o dono de produto, arquitetura, design e roadmap; `AGENTS.md` e os `AGENTS.md` de projeto são
  a memória de regras e comandos. **Uma informação tem um dono só**; os demais arquivos apontam para ele
  pelo nome do documento e da seção, sem repetir o conteúdo.
- A documentação descreve funcionalidades e contratos. Não escreva registros de mudanças, justificativas
  de decisões pontuais nem notas sobre exceções de planejamento.
- Documentação em pt-BR; nomes de código, endpoints e variáveis em inglês (ou em pt-BR quando o contrato
  da Central exigir). Diagramas em Mermaid, versionáveis e revisáveis como texto.
- Não documente código morto como se estivesse ativo. Se um endpoint é placeholder, diga explicitamente.
- O PDF permanece como spec de origem; o Apêndice A guarda o registro de uso de IA exigido pela entrega.
- Toda tarefa que cria um comando novo (suíte de testes, lint, etc.) o acrescenta à Verificação completa.

## Restrições arquiteturais

- Apenas `backend/` e `frontend/` como pastas de código; Central simulada dentro do backend; `docs/` é
  somente documentação.
- Domínio livre de frameworks; dependências sempre apontando para o domínio.
- Controllers não acessam repositórios; componentes React não acessam API diretamente.
- Entidades JPA não vazam para o domínio nem para respostas HTTP; conversão explícita nos adapters.
- Nenhuma dependência nova sem justificativa na spec da funcionalidade; o README registra apenas a
  versão na seção "Tecnologias".
- Sem router, estado global, UI kit ou ORM alternativo sem aprovação explícita — o pedido foi "frontend
  de forma simplificada".
- PostgreSQL é o padrão da aplicação (`compose.yaml` na raiz); H2 fica apenas nos testes. Os dados do
  Compose sobrevivem a restart do container.
- O repositório Git existe: não reinicialize (`git init`), não reescreva histórico e não altere remotes
  sem solicitação.

## Processo obrigatório de SDD (Spec-Driven Development)

Nenhuma implementação começa sem spec. O fluxo é **sequencial**; não pule etapas.

1. **Consultar** o PDF, os requisitos do usuário, `docs/`, este arquivo, os `AGENTS.md` de projeto e o
   README.
2. **Localizar a spec aplicável:** para uma melhoria do roadmap, use as specs existentes e não crie
   artefatos de planejamento; para uma nova funcionalidade, siga PRD → spec → tarefa → plano, conforme
   "Planejamento". Registre comportamento observável, critérios de aceite e contratos afetados na spec
   existente quando necessário. Se algo estiver ambíguo, pergunte antes de implementar.
3. **Desenhar contratos antes do código:** ports, assinaturas, DTOs, eventos e tabelas. Se houver
   mudança de contrato, atualize README e `docs/lld.md` nesta etapa.
4. **Escrever testes de aceite (red):** pelo menos um teste por critério crítico; execute e confirme que
   falham pelo motivo esperado.
5. **Implementar a menor fatia (green):** apenas o necessário para os testes passarem; manter os demais
   TODOs explícitos.
6. **Refatorar (refactor):** simplificar sem alterar comportamento; rodar os testes novamente.
7. **Verificar:** executar a Verificação completa de todos os projetos tocados.
8. **Documentar e reportar:** atualizar apenas o que descreve funcionalidades e contratos — roadmap,
   PRD/spec e README quando houver mudança de execução, API ou esquema. Relate mudanças, testes
   executados, riscos e próximos passos na resposta, não em arquivos de registro de alterações.

**Gates:** se a spec mudar durante a implementação, volte à etapa 2; mude o plano somente com a
autorização do usuário. Se a solicitação estiver fora do escopo, registre-a no roadmap e pare. Se um
teste não puder ser escrito, explique o motivo antes de implementar.

## Convenções de Git

- **Regra absoluta:** o agente **nunca faz commit por iniciativa própria** — nem em `main`, nem em
  branch de trabalho, nem ao concluir tarefa, rodar testes ou atualizar documentação. `git add` e
  `git commit` só são executados quando o usuário pedir expressamente **naquela interação**, informando
  o que deve ser versionado. Autorizações dadas em etapas anteriores não se acumulam.
- **Branch principal:** `main`. Não crie commit nela sem autorização expressa e específica do usuário.
- **Branches de trabalho:** `feature/*`, `bugfix/*`, `hotfix/*` e `docs/*`, criadas a partir da principal
  e integradas por pull request. A criação da branch também depende de pedido do usuário.
- **Commits:** quando autorizados, sejam curtos e descritivos, focados no **porquê** da mudança. O plano
  de uma tarefa, se o usuário pedir para versioná-lo, vai em commit próprio.
- **Push:** nunca faça push sem autorização expressa do usuário para aquele push.
- **Merge:** o merge é decisão do usuário. O agente só o faz quando o usuário autoriza expressamente
  aquele PR ou aquela tarefa. Sem autorização expressa, não faça merge; uma negação de `gh pr merge`
  não se contorna por outro caminho.

## Consulta à documentação de bibliotecas

Quando precisar consultar documentação de bibliotecas, use `context7` para obter informações atualizadas.
Se não estiver disponível, use a documentação oficial via `webfetch`/`websearch`. Nunca invente APIs ou
assinaturas a partir da memória do modelo.

## Agentes e skills

Sempre carregue as skills necessárias para a tarefa. Há skills de convenções e boas práticas
(`code-review`, `codebase-design`, `diagnosing-bugs`, `domain-modeling`, `report`, `research`, `tdd`,
`writing-for-agents`, entre outras), além das específicas de domínio. Carregue `prd`, `plan-task`, `hld`
e `lld` quando estiverem disponíveis para as tarefas de planejamento e arquitetura descritas acima.

Quando uma skill divergir dos arquivos de memória (`AGENTS.md` e rules), o que está no `AGENTS.md`
prevalece.

## Instruções obrigatórias para os agentes

1. **Sempre consultar as especificações antes de implementar.** PDF, requisitos do usuário, `docs/`,
   `AGENTS.md` e README, nessa ordem de precedência.
2. **Não implementar funcionalidades fora do escopo.** Regras de negócio só entram quando
   explicitamente solicitadas e planejadas.
3. **Não alterar contratos sem atualizar as especificações.** Endpoints, DTOs, ports, schema e status
   HTTP mudam junto com README, `docs/lld.md`, testes e plano/spec da tarefa.
4. **Não adicionar dependências sem justificativa.** Prefira o que já está no stack; o README registra
   apenas a versão.
5. **Não duplicar responsabilidades.** Um port por caso de uso, um dono por regra, uma fonte de verdade
   por informação.
6. **Não ignorar validações e tratamento de erros.** Inclusive falhas parciais, timeouts e respostas
   inesperadas da Central; mostre erros ao usuário.
7. **Criar testes para as funcionalidades implementadas.** Sem teste, a funcionalidade não está pronta.
8. **Executar testes antes de considerar uma tarefa concluída.** Backend e frontend, com os comandos
   oficiais.
9. **Atualizar documentação quando necessário.** Só funcionalidades e contratos, no arquivo dono, com
   ponteiros nos demais; não registre cada modificação técnica.
10. **Manter o código simples, legível e sustentável.** Soluções pequenas e óbvias vencem soluções
    clever; complexidade precisa de justificativa escrita.
11. **Não versionar por conta própria.** Sem pedido expresso do usuário, deixe as mudanças no
    working tree; não execute `git add`, `git commit`, `git push` nem merge. Concluir a verificação não
    autoriza commit.

## Verificação completa

É a sequência de comandos que fecha uma tarefa, rodada uma vez antes de abrir o PR, para cada projeto que
a tarefa tocou. Uma tarefa que cria um comando que a verificação deve rodar (uma suíte nova, por exemplo)
o acrescenta a esta seção e ao `AGENTS.md` do projeto correspondente.

```bash
(cd backend && mvn test)
(cd frontend && npm test && npm run build)
```

Comandos específicos: `backend/AGENTS.md` e `frontend/AGENTS.md`.

---
