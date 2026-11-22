package com.portability.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portability.adapters.out.persistence.entity.OperationJpaEntity;

import java.util.Optional;

public interface SpringDataOperationRepository extends JpaRepository<OperationJpaEntity, String> {

    Optional<OperationJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
