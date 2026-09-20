package com.baakio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "idempotency_record")
@IdClass(IdempotencyRecordId.class)
public class IdempotencyRecord {
    @Id
    @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "operation", nullable = false, length = 40)
    @Getter
    private String operation;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    @Getter
    private long resourceId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant createdAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String businessId, String idempotencyKey, String operation,
                             String resourceType, long resourceId) {
        this.businessId = businessId;
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

}
