package com.baakio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "scan_job")
@Getter
@Setter
public class ScanJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;

    @Column(name = "customer_id", nullable = false)
    private long customerId;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "mime_type", nullable = false, length = 40)
    private String mimeType;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "upload_idempotency_key", nullable = false, length = 120)
    private String uploadIdempotencyKey;

    @Column(name = "keep_attachment", nullable = false)
    private boolean keepAttachment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "image_deleted_at")
    private Instant imageDeletedAt;

    protected ScanJob() {}

    public ScanJob(String businessId, long customerId, String objectKey, String sha256,
                         String mimeType, String uploadIdempotencyKey, boolean keepAttachment) {
        this.businessId = businessId;
        this.customerId = customerId;
        this.objectKey = objectKey;
        this.sha256 = sha256;
        this.mimeType = mimeType;
        this.status = "QUEUED";
        this.uploadIdempotencyKey = uploadIdempotencyKey;
        this.keepAttachment = keepAttachment;
        this.createdAt = Instant.now();
    }
}
