# Spec — F02 Consultar e visualizar a agenda de descontos

> **Dono:** decisões de implementação e teste desta funcionalidade. Requisitos em [`prd.md`](prd.md);
> fluxo em [`../../hld.md`](../../hld.md), convenções de ports/mapeamento em
> [`../../lld.md`](../../lld.md), contratos HTTP em [`../../README.md`](../../README.md).

## Escopo

Verificação de autorização, caso de uso, tradução do contrato da Central, mock da Central, endpoint da
API, coluna de convênio no painel e testes. As decisões comuns a todas as funcionalidades — transações,
erros, correlation id, métricas e assemblagem dos serviços — estão em [`../../lld.md`](../../lld.md).

## Contratos

### API da aplicação

| Método | Rota | Situação | Resposta |
| --- | --- | --- | --- |
| `GET` | `/api/beneficiaries/{beneficiaryId}/agenda` | beneficiário autorizado | `200` com `DiscountInstallment[]` |
| `GET` | `/api/beneficiaries/{beneficiaryId}/agenda` | sem autorização | `403` com `{ "message": "Beneficiary not authorized" }` |
| `GET` | `/api/beneficiaries/{beneficiaryId}/agenda` | `beneficiaryId` em branco | `400` com `{ "message": "...", "fields": {...} }` |
| `GET` | `/api/beneficiaries/{beneficiaryId}/agenda` | falha na Central | `502` com `{ "message": "..." }` |

Corpo de sucesso (array simples, sem envelope):

```json
[
  {
    "installmentId": "INS-1",
    "agreementId": "AGR-001",
    "amount": 120.5,
    "discountDate": "2099-01-10",
    "status": "OPEN"
  }
]
```

### Central simulada

`GET /central/beneficiarios/{beneficiaryId}/agenda` responde `200` com a lista em pt-BR:

```json
[
  {
    "parcelaId": "INS-1",
    "convenioId": "AGR-001",
    "valor": 120.5,
    "dataDesconto": "2099-01-10",
    "status": "ABERTA"
  }
]
```

A fixture é determinística: `BEN-123` tem três parcelas — uma `ABERTA` futura (`INS-1`), uma `ABERTA`
passada (`INS-2`) e uma `PORTADA` futura (`INS-3`); qualquer outro identificador devolve `[]`. A
consulta não tem corpo nem assinatura; a validação de HMAC continua restrita às chamadas com corpo,
conforme [`../../lld.md`](../../lld.md).

### Ports e modelo

- `AuthorizationRepository.existsByBeneficiaryId(BeneficiaryId): boolean` responde se há ao menos uma
  autorização do beneficiário, sem exigir convênio.
- `GetDiscountAgendaUseCase.getAgenda(BeneficiaryId)` e `DiscountAgendaPort.fetchAgenda(BeneficiaryId)`
  usam o value object; `GetDiscountAgendaService` implementa o caso de uso.
- `BeneficiaryNotAuthorizedException` (domínio, sem framework) representa a violação da regra de
  autorização; a borda HTTP a traduz em `403`.
- `DiscountInstallment` rejeita identificadores em branco e campos obrigatórios nulos no construtor,
  por meio de `Validation`.

## Decisões de implementação

- `GetDiscountAgendaService` valida o `beneficiaryId`, consulta
  `AuthorizationRepository.existsByBeneficiaryId` e lança `BeneficiaryNotAuthorizedException` quando não
  há autorização. Com autorização, chama `DiscountAgendaPort.fetchAgenda` e devolve a lista completa, sem
  filtrar por data ou status; a elegibilidade de seleção permanece no registro (F03).
- `CentralClient.fetchAgenda` usa o `RestClient` já configurado (timeout de 2 s) para fazer o `GET`,
  traduz `parcelaId`, `convenioId`, `valor`, `dataDesconto` e `status` para o modelo em inglês conforme
  [`../../lld.md`](../../lld.md) e converte status `ABERTA`/`PORTADA`/`CANCELADA` para
  `OPEN`/`PORTED`/`CANCELLED`. Qualquer status HTTP diferente de `200`, corpo ausente/inválido, data fora
  do ISO `AAAA-MM-DD` ou status desconhecido vira `CentralUnavailableException`.
- `MockCentralController.getAgenda` devolve a fixture acima; o mock usa o mesmo código de serialização da
  Central para os endpoints com assinatura.
- `AgendaController` injeta `GetDiscountAgendaUseCase` e converte o resultado para o DTO de resposta
  `InstallmentResponse`. `ApiExceptionHandler` traduz `InvalidRequestException` (`400`, com `fields`),
  `BeneficiaryNotAuthorizedException` (`403`) e `CentralUnavailableException` (`502`).
- `AuthorizationRepositoryAdapter` implementa `existsByBeneficiaryId` com uma query derivada do
  Spring Data, sem alterar o schema.
- Frontend: a tabela de parcelas ganha a coluna "Convênio" com `agreementId`; o restante do
  comportamento (seleção desabilitada para parcelas não elegíveis, estado vazio e alerta de erro) já
  existe e é verificado por testes.

## Decisões de teste

- `GetDiscountAgendaServiceTest` (unitário, fakes dos ports): agenda com autorização, sem autorização sem
  chamar a Central, `beneficiaryId` em branco e falha da Central sem devolver lista parcial.
- `CentralClientTest` (HTTP em processo): tradução da resposta da Central, falha HTTP e resposta com
  status desconhecido.
- `AgendaControllerContractTest` (`MockMvc`, caso de uso mockado): `200`, `403`, `400` e `502`, sempre
  com o corpo de erro no formato `{ "message": "..." }`.
- `MockCentralAgendaTest` (em processo): fixture de `BEN-123` e lista vazia para ID desconhecido.
- `App.test.tsx`: exibição de convênio/valor/data/status, estado vazio e mensagem de erro da consulta.

Nenhuma dependência nova: `RestClient`, Jackson e as bibliotecas de teste já estão no projeto.
