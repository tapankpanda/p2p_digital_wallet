package com.tapan.p2pdigitalwallet.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key"))
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "reference_id", nullable = false, length = 40, updatable = false)
    private String referenceId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord(){
    }

    public IdempotencyRecord(String idempotencyKey, String requestHash, String referenceId, String status) {
        this.idempotencyKey = Objects.requireNonNull(
                idempotencyKey, "Idempotency key is required"
        );
        this.requestHash = Objects.requireNonNull(
                requestHash, "Request hash is required"
        );
        this.referenceId = Objects.requireNonNull(
                referenceId, "Reference ID is required"
        );
        this.status = Objects.requireNonNull(
                status, "Status is required"
        );
    }

    @PrePersist
    void onCreate(){
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
