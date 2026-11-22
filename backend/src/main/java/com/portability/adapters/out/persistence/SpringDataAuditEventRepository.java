package com.portability.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portability.adapters.out.persistence.entity.AuditEventJpaEntity;

import java.util.List;

public interface SpringDataAuditEventRepository extends JpaRepository<AuditEventJpaEntity, String> {

    List<AuditEventJpaEntity> findByOperationIdOrderByOccurredAtAsc(String operationId);
}
