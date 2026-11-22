package com.portability.adapters.out.transaction;

import com.portability.domain.ports.out.TransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Demarcates transactions with Spring at the boundary, invisible to the application. */
@Component
public class TransactionalAdapter implements TransactionPort {

    private final TransactionTemplate transactionTemplate;

    public TransactionalAdapter(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
