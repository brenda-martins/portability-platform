package com.portability.application.usecase;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.BackoffPort;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.CentralUnavailableException;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.IdGenerator;
import com.portability.domain.ports.out.MetricsPort;
import com.portability.domain.ports.out.OperationRepository;
import com.portability.domain.ports.out.TransactionPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** In-memory ports shared by the use-case unit tests. */
final class TestDoubles {

    private TestDoubles() {
    }

    static final class State {

        final Map<String, PortabilityOperation> operations = new LinkedHashMap<>();
        final Map<String, PortabilityOperation> operationsByKey = new LinkedHashMap<>();
        final List<AuditEvent> auditEvents = new ArrayList<>();
        final List<Authorization> authorizations = new ArrayList<>();

        Runnable snapshot() {
            Map<String, PortabilityOperation> operationsCopy = new LinkedHashMap<>(operations);
            Map<String, PortabilityOperation> keysCopy = new LinkedHashMap<>(operationsByKey);
            List<AuditEvent> eventsCopy = new ArrayList<>(auditEvents);
            List<Authorization> authorizationsCopy = new ArrayList<>(authorizations);
            return () -> {
                operations.clear();
                operations.putAll(operationsCopy);
                operationsByKey.clear();
                operationsByKey.putAll(keysCopy);
                auditEvents.clear();
                auditEvents.addAll(eventsCopy);
                authorizations.clear();
                authorizations.addAll(authorizationsCopy);
            };
        }
    }

    static final class Transactions implements TransactionPort {

        private final State state;

        Transactions(State state) {
            this.state = state;
        }

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            Runnable rollback = state.snapshot();
            try {
                return work.get();
            } catch (RuntimeException exception) {
                rollback.run();
                throw exception;
            }
        }
    }

    static final class Operations implements OperationRepository {

        private final State state;

        Operations(State state) {
            this.state = state;
        }

        boolean failOnSave;

        @Override
        public PortabilityOperation save(PortabilityOperation operation, String idempotencyKey) {
            if (failOnSave) {
                throw new ConcurrentOperationException("Concorrência ao salvar");
            }
            PortabilityOperation stored = nextVersion(operation);
            state.operations.put(stored.operationId(), stored);
            state.operationsByKey.put(idempotencyKey, stored);
            return stored;
        }

        @Override
        public PortabilityOperation save(PortabilityOperation operation) {
            if (failOnSave) {
                throw new ConcurrentOperationException("Concorrência ao salvar");
            }
            PortabilityOperation current = state.operations.get(operation.operationId());
            if (current == null || current.version() != operation.version()) {
                throw new ConcurrentOperationException("Versão desatualizada");
            }
            PortabilityOperation stored = nextVersion(operation);
            state.operations.put(stored.operationId(), stored);
            state.operationsByKey.replaceAll((key, value) ->
                    value.operationId().equals(stored.operationId()) ? stored : value);
            return stored;
        }

        @Override
        public Optional<PortabilityOperation> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(state.operationsByKey.get(idempotencyKey));
        }

        @Override
        public Optional<PortabilityOperation> findById(String operationId) {
            return Optional.ofNullable(state.operations.get(operationId));
        }

        void seed(PortabilityOperation operation, String idempotencyKey) {
            state.operations.put(operation.operationId(), operation);
            state.operationsByKey.put(idempotencyKey, operation);
        }

        private static PortabilityOperation nextVersion(PortabilityOperation operation) {
            return new PortabilityOperation(
                    operation.operationId(), operation.beneficiaryId(), operation.installmentIds(),
                    operation.creditorAssignee(), operation.receivingAccount(), operation.status(),
                    operation.protocol(), operation.rejectionReason(), operation.requestedAt(),
                    operation.version() + 1);
        }
    }

    static final class AuditEvents implements AuditEventRepository {

        private final State state;

        AuditEvents(State state) {
            this.state = state;
        }

        boolean failOnAppend;

        @Override
        public AuditEvent append(AuditEvent event) {
            if (failOnAppend) {
                throw new IllegalStateException("audit unavailable");
            }
            state.auditEvents.add(event);
            return event;
        }

        @Override
        public List<AuditEvent> findByOperationId(String operationId) {
            return state.auditEvents.stream()
                    .filter(event -> event.operationId().equals(operationId))
                    .toList();
        }
    }

    static final class Authorizations implements AuthorizationRepository {

        private final State state;

        Authorizations(State state) {
            this.state = state;
        }

        @Override
        public Authorization save(Authorization authorization) {
            state.authorizations.add(authorization);
            return authorization;
        }

        @Override
        public Optional<Authorization> findByBeneficiaryAndAgreement(String beneficiaryId, String agreementId) {
            return state.authorizations.stream()
                    .filter(authorization -> authorization.beneficiaryId().equals(beneficiaryId)
                            && authorization.agreementId().equals(agreementId))
                    .findFirst();
        }

        @Override
        public boolean existsByBeneficiaryId(String beneficiaryId) {
            return state.authorizations.stream()
                    .anyMatch(authorization -> authorization.beneficiaryId().equals(beneficiaryId));
        }
    }

    static final class CentralAuthorization implements CentralAuthorizationPort {

        String authorizationId = "AUT-1";
        RuntimeException failure;

        @Override
        public String registerAuthorization(String beneficiaryId, String agreementId) {
            if (failure != null) {
                throw failure;
            }
            return authorizationId;
        }
    }

    static final class Central implements CentralPortabilityPort {

        String protocol = "PRT-1";
        final Deque<CentralOperationStatus> statuses = new ArrayDeque<>();
        final List<ReceivingAccount> accountUpdates = new ArrayList<>();
        RuntimeException registrationFailure;
        RuntimeException statusFailure;
        RuntimeException accountFailure;

        @Override
        public String registerPortability(String beneficiaryId, List<String> installmentIds,
                String creditorAssignee, ReceivingAccount receivingAccount) {
            if (registrationFailure != null) {
                throw registrationFailure;
            }
            return protocol;
        }

        @Override
        public CentralOperationStatus consultStatus(String protocol) {
            if (statusFailure != null) {
                throw statusFailure;
            }
            return statuses.isEmpty()
                    ? new CentralOperationStatus(com.portability.domain.model.OperationStatus.PENDING, null, null)
                    : statuses.removeFirst();
        }

        @Override
        public ReceivingAccount updateReceivingAccount(String protocol, ReceivingAccount receivingAccount) {
            if (accountFailure != null) {
                throw accountFailure;
            }
            accountUpdates.add(receivingAccount);
            return receivingAccount;
        }
    }

    static final class Agenda implements DiscountAgendaPort {

        List<DiscountInstallment> installments = List.of();
        RuntimeException failure;

        @Override
        public List<DiscountInstallment> fetchAgenda(String beneficiaryId) {
            if (failure != null) {
                throw failure;
            }
            return installments;
        }
    }

    static final class Backoff implements BackoffPort {

        final List<Duration> delays = new ArrayList<>();
        boolean interrupt;

        @Override
        public void await(Duration duration) throws InterruptedException {
            delays.add(duration);
            if (interrupt) {
                throw new InterruptedException("interrompido");
            }
        }
    }

    static final class Ids implements IdGenerator {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public String next() {
            return "ID-" + counter.incrementAndGet();
        }
    }

    static final class Metrics implements MetricsPort {

        final List<String> names = new ArrayList<>();

        @Override
        public void increment(String metric) {
            names.add(metric);
        }
    }

    static Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    static CentralUnavailableException centralFailure(String message) {
        return new CentralUnavailableException(message);
    }
}
