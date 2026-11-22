package com.portability.adapters.out.persistence;

import com.portability.domain.model.OperationStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portability_operations")
public class OperationJpaEntity {

    @Id
    @Column(name = "operation_id")
    private String operationId;

    @Column(name = "beneficiary_id", nullable = false)
    private String beneficiaryId;

    @ElementCollection
    @CollectionTable(name = "operation_installments", joinColumns = @jakarta.persistence.JoinColumn(name = "operation_id"))
    @Column(name = "installment_id")
    private List<String> installmentIds = new ArrayList<>();

    @Column(name = "creditor_assignee")
    private String creditorAssignee;

    @Column(name = "bank")
    private String bank;

    @Column(name = "agency")
    private String agency;

    @Column(name = "account")
    private String account;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OperationStatus status;

    @Column(name = "protocol")
    private String protocol;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
}
