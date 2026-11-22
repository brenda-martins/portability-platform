# Spec — F01 Autorizar o beneficiário (opt-in)

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP e idioma das mensagens em
> [`../../README.md`](../../README.md), seção "Documentação da API".

## Escopo

Validação na borda, caso de uso, persistência, integração assinada com a Central, endpoint da API,
ajuste do painel e testes. As decisões comuns a todas as funcionalidades — transações, erros,
correlation id, métricas e assemblagem dos serviços — estão em [`../../lld.md`](../../lld.md).

## Contratos

### API da aplicação

| Método | Rota | Situação | Resposta |
| --- | --- | --- | --- |
| `POST` | `/api/authorizations` | solicitação nova | `201` com `Authorization` |
| `POST` | `/api/authorizations` | par já autorizado | `200` com a `Authorization` existente |
| `POST` | `/api/authorizations` | par já autorizado em corrida | `409` com `{ "message": "..." }` |
| `POST` | `/api/authorizations` | campo ausente/em branco | `400` com `{ "message": "...", "fields": {...} }` |
| `POST` | `/api/authorizations` | falha na Central | `502` com `{ "message": "..." }` |

Corpo da solicitação: `{ "beneficiaryId": "BEN-123", "agreementId": "AGR-001" }`.
Corpo de sucesso: `{ "authorizationId": "AUT-...", "beneficiaryId": "BEN-123",
"agreementId": "AGR-001", "createdAt": "2026-11-22T12:34:56Z" }`.

### Central simulada

`POST /central/autorizacoes` recebe `{ "beneficiarioId": "...", "convenioId": "...",
"assinatura": "..." }` com o cabeçalho `X-Signature` contendo o HMAC-SHA256 em Base64 do corpo canônico
serializado sem `assinatura`. Responde `201` com `{ "autorizacaoId": "AUT-..." }`, `400` para campos
inválidos e `401` para assinatura ausente/inválida.

### Ports

`CentralAuthorizationPort.registerAuthorization(BeneficiaryId, AgreementId): String` devolve o
`autorizacaoId` da Central. `CentralClient` implementa o port e usa `CentralRequestCodec` para montar o
payload em pt-BR, assinar o corpo canônico e enviar o HMAC no campo `assinatura` e no cabeçalho.

`RegisterAuthorizationUseCase.registerAuthorization(BeneficiaryId, AgreementId): AuthorizationResult`
devolve a autorização e o indicador `created`, para que a borda HTTP escolha entre `201` e `200`.

## Decisões de implementação

- `BeneficiaryId` e `AgreementId` rejeitam valores em branco no construtor, por meio de `Validation`;
  `Authorization` recebe os value objects. O DTO `AuthorizeRequest` chama `validate()` na borda; a
  invariant tem um único dono no domínio.
- `RegisterAuthorizationService` recebe os value objects validados, executa a consulta/gravação em uma
  unidade de trabalho (`TransactionPort`), consulta `AuthorizationRepository.findByBeneficiaryAndAgreement`
  e devolve a
  autorização existente com `created = false`. Caso contrário, chama `CentralAuthorizationPort`, cria a
  `Authorization` com o id devolvido e `createdAt = Instant.now(clock)`, salva no repositório e devolve
  `created = true`. `Clock`, `TransactionPort` e `MetricsPort` são injetados. Falha da Central não gera
  gravação local.
- `CentralClient` usa `RestClient` com timeout de conexão e leitura de 2 segundos e converte qualquer
  resposta diferente de `201` ou falha de transporte em `CentralUnavailableException`.
- `HmacSignatureService` calcula HMAC-SHA256 em Base64 com `central.hmac-secret` e compara em tempo
  constante; `CentralRequestCodec` centraliza a serialização canônica e a verificação usadas pelo cliente
  e pelo mock.
- `AuthorizationRepositoryAdapter` converte explicitamente entre `Authorization` e
  `AuthorizationJpaEntity`; a tabela `authorizations` e a query derivada já existentes são reaproveitadas,
  sem mudança de schema. O par beneficiário+convênio é único.
- `AuthorizationController` injeta somente `RegisterAuthorizationUseCase`, chama `request.validate()` e
  escolhe o status pelo indicador `created`. A resposta usa o DTO `AuthorizationResponse`, sem tipos do
  domínio. `ApiExceptionHandler` traduz `InvalidRequestException` em `400` (com `fields`) e
  `CentralUnavailableException` em `502`, sempre com `message` e `correlationId`.
- Frontend: `Authorization` ganha o campo `createdAt`; o painel passa a enviar o corpo pelo DTO
  `AuthorizeRequest`. O wrapper `apiFetch` passa a usar o `message` do corpo de erro, exibido pelo painel.

## Decisões de teste

- `RegisterAuthorizationServiceTest` (unitário, fakes dos ports): cria autorização, reenvio idempotente,
  id em branco rejeitado no construtor do value object e falha da Central sem persistência.
- `HmacSignatureServiceTest` (unitário): assinatura válida, verificação válida e rejeição de assinatura
  divergente.
- `CentralClientTest` (HTTP em processo): payload e `X-Signature` enviados e tradução de resposta de erro
  da Central.
- `AuthorizationControllerContractTest` (`MockMvc`, caso de uso mockado): `201`, `200` no reenvio,
  `400`, `409` e `502` com corpo de erro.
- `AuthorizationRepositoryAdapterTest` (`@SpringBootTest` com o perfil `test`/H2): leitura por
  beneficiário e convênio e rejeição de par duplicado pela unicidade.
- `MockCentralAuthorizationTest` (em processo): `201` com assinatura válida e `401` com assinatura
  inválida.
- Frontend: ajustar as fixtures de `Authorization` em `App.test.tsx` e verificar que a mensagem de erro
  devolvida pela API é exibida no painel.

Nenhuma dependência nova: `RestClient`, Jackson e as bibliotecas de teste já vêm do
`spring-boot-starter-web` e do `pom.xml`.
