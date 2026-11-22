package com.portability.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataOperationRepository extends JpaRepository<OperationJpaEntity, String> {

    Optional<OperationJpaEntity> findByProtocol(String protocol);
}
