package com.portability.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "authorizations")
public class AuthorizationJpaEntity {

    @Id
    @Column(name = "authorization_id")
    private String authorizationId;

    @Column(name = "beneficiary_id", nullable = false)
    private String beneficiaryId;

    @Column(name = "agreement_id", nullable = false)
    private String agreementId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
