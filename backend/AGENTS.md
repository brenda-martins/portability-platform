# AGENTS.md — backend

Projeto: `backend/` — Java 21 + Spring Boot 4.2.0 + Maven + PostgreSQL (H2 nos testes), arquitetura
hexagonal.

## Documentos de referência

- Convenções, regras de negócio, Git e SDD: [`../AGENTS.md`](../AGENTS.md).
- Visão geral e fluxos: [`../docs/hld.md`](../docs/hld.md).
- Detalhes de implementação (ports, mapeamentos, persistência): [`../docs/lld.md`](../docs/lld.md).
- Vocabulário: [`../GLOSSARY.md`](../GLOSSARY.md).
- Execução e contratos: [`../README.md`](../README.md).

## Stack e comandos

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.3-tem"
export PATH="$JAVA_HOME/bin:$PATH"

mvn spring-boot:run   # API em http://localhost:8080
mvn test              # smoke test do contexto
mvn clean package     # gera target/portability-backend-0.0.1-SNAPSHOT.jar
```

O Java padrão do ambiente é 17: **sempre** exportar `JAVA_HOME` para o JDK 21 antes de usar o Maven.
O backend não é containerizado: rode o Maven diretamente na máquina. O PostgreSQL de desenvolvimento
sobe com `docker compose up -d postgres` a partir da raiz do repositório; `mvn test` usa H2 e não
depende do Compose.

## Práticas específicas

- `domain` sem Spring/JPA/HTTP; `application` depende só de `domain`; adapters implementam ports.
- Injeção por construtor, `record` para modelos, `BigDecimal` para dinheiro, `Optional` para ausência.
- Anotações de framework apenas nos adapters e na aplicação; nunca no domínio.
- Stubs lançam `UnsupportedOperationException("TODO: ...")` ou retornam `501`; nunca dados falsos.
- A tradução do contrato da Central (pt-BR) para o modelo interno (inglês) fica em
  `adapters/out/central`.
- Eventos de auditoria são append-only; nunca atualize ou apague um evento.

## Verificação completa

Rodar antes de abrir um PR de uma tarefa que tocou o backend:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.3-tem"
(cd backend && mvn test)
```

Se a tarefa criar uma nova suíte de testes ou comando de verificação, acrescente-o aqui e na seção
"Verificação completa" do [`../AGENTS.md`](../AGENTS.md).
