package com.portability.domain.ports.out;

import java.util.function.Supplier;

public interface TransactionPort {

    <T> T inTransaction(Supplier<T> work);
}
