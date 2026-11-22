package com.portability.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_reservations")
public class IdempotencyReservationJpaEntity {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    public IdempotencyReservationJpaEntity() {
    }

    public IdempotencyReservationJpaEntity(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
