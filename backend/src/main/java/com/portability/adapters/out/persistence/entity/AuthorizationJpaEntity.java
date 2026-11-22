package com.portability.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "authorizations", uniqueConstraints = @UniqueConstraint(
        name = "uk_authorizations_beneficiary_agreement",
        columnNames = {"beneficiary_id", "agreement_id"}))
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

    public AuthorizationJpaEntity() {
    }

    public String getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(String authorizationId) {
        this.authorizationId = authorizationId;
    }

    public String getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(String beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public void setAgreementId(String agreementId) {
        this.agreementId = agreementId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
