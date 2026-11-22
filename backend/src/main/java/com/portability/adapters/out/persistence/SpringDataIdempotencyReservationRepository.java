package com.portability.adapters.out.persistence;

import com.portability.adapters.out.persistence.entity.IdempotencyReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataIdempotencyReservationRepository
        extends JpaRepository<IdempotencyReservationJpaEntity, String> {
}
