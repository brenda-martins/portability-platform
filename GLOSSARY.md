# Glossário — Portability Platform

Vocabulário do domínio do produto. Use estes termos nos documentos, no planejamento e nas conversas.
A coluna "Código / API" é o identificador adotado no código e no contrato `/api` (inglês); o contrato da
Central usa os nomes em português do PDF e é traduzido no adapter.

| Termo (pt-BR) | Código / API | Definição |
| --- | --- | --- |
| Beneficiário | `beneficiaryId` | Pessoa cujos descontos em folha podem ser portados. |
| Convênio | `agreementId` | Convênio de origem, atual titular dos descontos. |
| Autorização | `Authorization`, `authorizationId` | `opt-in` do beneficiário vinculado a um convênio; habilita a consulta à agenda. |
| Agenda de descontos | `DiscountInstallment`, `getAgenda` | Lista de parcelas futuras de um beneficiário autorizado. |
| Parcela | `installmentId` | Desconto futuro elegível à portabilidade. |
| Operação de portabilidade | `PortabilityOperation`, `operationId` | Solicitação de transferência das parcelas para o novo credor. |
| Credor cessionário | `creditorAssignee` | Nova credora que passa a receber as parcelas portadas. |
| Conta de recebimento | `ReceivingAccount` | Conta banco/agência/conta que receberá as parcelas após a confirmação. |
| Protocolo | `protocol` / `protocolo` | Identificador fornecido pela Central para acompanhar uma operação; não é gerado localmente. |
| Status da operação | `OperationStatus` | `PENDING` → `CONFIRMED` ou `REJECTED` (`PENDENTE` / `CONFIRMADA` / `REJEITADA` na Central). |
| Status da parcela | `InstallmentStatus` | `OPEN` (aberta, elegível), `PORTED` (portada), `CANCELLED` (cancelada). |
| Evento de auditoria | `AuditEvent` | Registro imutável de uma mudança relevante de estado de uma operação. |
| Trilha de auditoria | `getAuditTrail` | Eventos de uma operação, ordenados por ocorrência; nunca sobrescritos. |
| Central de Convênios | `CentralClient` / `MockCentralController` | Sistema externo que concentra autorizações e agenda. Aqui é simulado em `/central`. |
| Confirmação assíncrona | `consultStatus`, `Retry-After` | Padrão em que a Central devolve protocolo imediatamente e o status chega depois por consulta. |
| Idempotência | `findByProtocol` | Propriedade de reenvios não gerarem operações duplicadas na Central nem localmente. |
| Assinatura | `signature` / `assinatura` | HMAC-SHA256 em Base64 usado para integridade das mensagens trocadas com a Central. |
| Backoff | — | Estratégia de espera progressiva entre tentativas de consulta de status. |
| Funcionalidade | — | Uma entrega demonstrável no produto, descrita pelo que a pessoa consegue fazer (ver `docs/roadmap.md`). |
| Tarefa | `F<NN>-T<NN>` | Parte entregável de uma funcionalidade, derivada da spec. |

## Regras de uso

- Se um termo novo aparecer, adicione-o aqui **antes** de usá-lo em documentos ou planejamento.
- Em nomes de arquivos e diretórios, use kebab-case sem acento (ex.: `01-autorizacao-do-beneficiario`).
- Não crie sinônimos para termos já registrados; se precisar de outro sentido, defina um termo novo.
- Este arquivo é o dono do vocabulário. Os outros documentos apontam para ele, não o repetem.
