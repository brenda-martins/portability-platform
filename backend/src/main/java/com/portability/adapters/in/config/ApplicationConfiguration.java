package com.portability.adapters.in.config;

import com.portability.application.GetDiscountAgendaService;
import com.portability.application.GetOperationAuditService;
import com.portability.application.GetOperationStatusService;
import com.portability.application.RegisterAuthorizationService;
import com.portability.application.StartPortabilityService;
import com.portability.application.UpdateReceivingAccountService;
import com.portability.domain.ports.in.GetDiscountAgendaUseCase;
import com.portability.domain.ports.in.GetOperationAuditUseCase;
import com.portability.domain.ports.in.GetOperationStatusUseCase;
import com.portability.domain.ports.in.RegisterAuthorizationUseCase;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.in.UpdateReceivingAccountUseCase;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.BackoffPort;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.IdGenerator;
import com.portability.domain.ports.out.MetricsPort;
import com.portability.domain.ports.out.OperationRepository;
import com.portability.domain.ports.out.TransactionPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Composition root: wires framework adapters into the framework-free use cases. */
@Configuration
@EnableConfigurationProperties(CentralProperties.class)
public class ApplicationConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RegisterAuthorizationUseCase registerAuthorizationUseCase(
            AuthorizationRepository authorizationRepository,
            CentralAuthorizationPort centralAuthorizationPort,
            TransactionPort transactions,
            Clock clock,
            MetricsPort metrics
        ) {
        return new RegisterAuthorizationService(authorizationRepository, centralAuthorizationPort,
                transactions, clock, metrics);
    }

    @Bean
    public GetDiscountAgendaUseCase getDiscountAgendaUseCase(
            AuthorizationRepository authorizationRepository,
            DiscountAgendaPort discountAgendaPort,
            MetricsPort metrics
        ) {
        return new GetDiscountAgendaService(authorizationRepository, discountAgendaPort, metrics);
    }

    @Bean
    public StartPortabilityUseCase startPortabilityUseCase(
            OperationRepository operationRepository,
            AuthorizationRepository authorizationRepository,
            AuditEventRepository auditEventRepository,
            DiscountAgendaPort discountAgendaPort,
            CentralPortabilityPort centralPortabilityPort,
            TransactionPort transactions,
            Clock clock,
            IdGenerator ids,
            MetricsPort metrics
        ) {
        return new StartPortabilityService(operationRepository, authorizationRepository,
                auditEventRepository, discountAgendaPort, centralPortabilityPort,
                transactions, clock, ids, metrics);
    }

    @Bean
    public GetOperationStatusUseCase getOperationStatusUseCase(
            OperationRepository operationRepository,
            AuditEventRepository auditEventRepository,
            CentralPortabilityPort centralPortabilityPort,
            BackoffPort backoffPort,
            TransactionPort transactions,
            Clock clock,
            IdGenerator ids,
            MetricsPort metrics
        ) {
        return new GetOperationStatusService(operationRepository, auditEventRepository,
                centralPortabilityPort, backoffPort, transactions, clock, ids, metrics);
    }

    @Bean
    public UpdateReceivingAccountUseCase updateReceivingAccountUseCase(
            OperationRepository operationRepository,
            AuditEventRepository auditEventRepository,
            CentralPortabilityPort centralPortabilityPort,
            TransactionPort transactions,
            Clock clock,
            IdGenerator ids,
            MetricsPort metrics
        ) {
        return new UpdateReceivingAccountService(operationRepository, auditEventRepository,
                centralPortabilityPort, transactions, clock, ids, metrics);
    }

    @Bean
    public GetOperationAuditUseCase getOperationAuditUseCase(
            OperationRepository operationRepository,
            AuditEventRepository auditEventRepository,
            MetricsPort metrics
        ) {
        return new GetOperationAuditService(operationRepository, auditEventRepository, metrics);
    }
}
