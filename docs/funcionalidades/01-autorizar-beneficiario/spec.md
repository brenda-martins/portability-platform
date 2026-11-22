# Spec — F01 Autorizar o beneficiário (opt-in)

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP em [`../../README.md`](../../README.md).

## Escopo

Uma fatia vertical: validação na borda, caso de uso, persistência, integração assinada com a Central,
endpoint da API, ajuste do painel e testes. Os demais fluxos continuam como stubs honestos.

## Contratos

### API da aplicação

| Método | Rota | Situação | Resposta |
| --- | --- | --- | --- |
| `POST` | `/api/authorizations` | solicitação nova | `201` com `Authorization` |
| `POST` | `/api/authorizations` | par já autorizado | `200` com a `Authorization` existente |
| `POST` | `/api/authorizations` | campo ausente/em branco | `400` com `{ "message": "..." }` |
| `POST` | `/api/authorizations` | falha na Central | `502` com `{ "message": "..." }` |

Corpo da solicitação: `{ "beneficiaryId": "BEN-123", "agreementId": "AGR-001" }`.
Corpo de sucesso: `{ "authorizationId": "AUT-...", "beneficiaryId": "BEN-123",
"agreementId": "AGR-001", "createdAt": "2026-11-22T12:34:56Z" }`.

### Central simulada

`POST /central/autorizacoes` recebe `{ "beneficiarioId": "...", "convenioId": "..." }` com o cabeçalho
`X-Signature` contendo o HMAC-SHA256 em Base64 do corpo exato enviado. Responde `201` com
`{ "autorizacaoId": "AUT-..." }`, `400` para campos inválidos e `401` para assinatura ausente/inválida.

### Ports

`CentralAuthorizationPort.registerAuthorization(beneficiaryId, agreementId): String` devolve o
`autorizacaoId` da Central. `CentralClient` passa a implementá-lo junto dos ports já existentes, montando
o payload em pt-BR e calculando a assinatura internamente, conforme a regra de conversão da Central no
adapter.

`RegisterAuthorizationUseCase.registerAuthorization(beneficiaryId, agreementId): AuthorizationResult`
devolve a autorização e o indicador `created`, para que a borda HTTP escolha entre `201` e `200`.

## Decisões de implementação

- `Authorization` rejeita `beneficiaryId` e `agreementId` em branco no construtor do domínio; o DTO da
  borda usa `@NotBlank` nos dois campos e `@Valid` no controller.
- `PortabilityApplicationService.registerAuthorization` valida os campos, consulta
  `AuthorizationRepository.findByBeneficiaryAndAgreement` e devolve a autorização existente com
  `created = false`. Caso contrário, chama `CentralAuthorizationPort`, cria a `Authorization` com o id
  devolvido e `Instant.now()`, salva no repositório e devolve `created = true`. A ocorrência de
  `CentralUnavailableException` não gera gravação local.
- `CentralClient` usa `RestClient` com timeout de conexão e leitura de 2 segundos e converte qualquer
  resposta diferente de `201` ou falha de transporte em `CentralUnavailableException`.
- `HmacSignatureService` calcula HMAC-SHA256 em Base64 com `central.hmac-secret` e compara em tempo
  constante na verificação; o mock da Central usa o mesmo port para validar o cabeçalho.
- `AuthorizationRepositoryAdapter` converte explicitamente entre `Authorization` e
  `AuthorizationJpaEntity`; a tabela `authorizations` e a query derivada já existentes são reaproveitadas,
  sem mudança de schema.
- `AuthorizationController` injeta somente `RegisterAuthorizationUseCase` e escolhe o status pelo
  indicador `created`. Um `@RestControllerAdvice` traduz `MethodArgumentNotValidException` e
  `IllegalArgumentException` em `400` e `CentralUnavailableException` em `502`, sempre com
  `{ "message": "..." }`.
- Frontend: `Authorization` ganha o campo `createdAt`; o painel passa a enviar o corpo pelo DTO
  `AuthorizeRequest`. O wrapper `apiFetch` passa a usar o `message` do corpo de erro, exibido pelo painel.

## Decisões de teste

- `PortabilityApplicationServiceTest` (unitário, fakes dos ports): cria autorização, reenvio idempotente,
  campos inválidos e falha da Central sem persistência.
- `HmacSignatureServiceTest` (unitário): assinatura válida, verificação válida e rejeição de assinatura
  divergente.
- `CentralClientTest` (HTTP em processo): payload e `X-Signature` enviados e tradução de resposta de erro
  da Central.
- `AuthorizationControllerContractTest` (`MockMvc`, caso de uso mockado): `201`, `200` no reenvio, `400`
  e `502` com corpo de erro.
- `MockCentralAuthorizationTest` (em processo): `201` com assinatura válida e `401` com assinatura
  inválida.
- Frontend: ajustar as fixtures de `Authorization` em `App.test.tsx` e verificar que a mensagem de erro
  devolvida pela API é exibida no painel.

Nenhuma dependência nova: `RestClient`, Jackson e Bean Validation já vêm do `spring-boot-starter-web` e
do `spring-boot-starter-validation` presentes no `pom.xml`.
