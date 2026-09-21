CREATE TABLE scan_job (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id CHAR(36) NOT NULL,
    customer_id BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    mime_type VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(80) NULL,
    upload_idempotency_key VARCHAR(120) NOT NULL,
    keep_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    locked_at DATETIME(6) NULL,
    claim_token CHAR(36) NULL,
    image_deleted_at DATETIME(6) NULL,
    UNIQUE KEY uq_scan_upload_key (business_id, upload_idempotency_key),
    KEY ix_scan_business_customer (business_id, customer_id, id),
    KEY ix_scan_cleanup (status, created_at, keep_attachment, image_deleted_at),
    KEY ix_scan_recovery (status, locked_at),
    CONSTRAINT fk_scan_business FOREIGN KEY (business_id) REFERENCES business(id),
    CONSTRAINT fk_scan_customer FOREIGN KEY (customer_id) REFERENCES customer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE scan_draft (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scan_job_id BIGINT NOT NULL,
    extracted_json LONGTEXT NOT NULL,
    edited_json LONGTEXT NOT NULL,
    written_total DECIMAL(19,2) NULL,
    calculated_total DECIMAL(19,2) NULL,
    flags_json LONGTEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    posted_purchase_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_scan_draft_job (scan_job_id),
    UNIQUE KEY uq_scan_draft_purchase (posted_purchase_id),
    CONSTRAINT fk_scan_draft_job FOREIGN KEY (scan_job_id) REFERENCES scan_job(id),
    CONSTRAINT fk_scan_draft_purchase FOREIGN KEY (posted_purchase_id) REFERENCES purchase(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE scan_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scan_job_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    error_code VARCHAR(80) NULL,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    UNIQUE KEY uq_scan_attempt_no (scan_job_id, attempt_no),
    CONSTRAINT fk_scan_attempt_job FOREIGN KEY (scan_job_id) REFERENCES scan_job(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id CHAR(36) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    claimed_until DATETIME(6) NULL,
    published_at DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    KEY ix_outbox_pending (published_at, claimed_until, id),
    CONSTRAINT fk_outbox_business FOREIGN KEY (business_id) REFERENCES business(id),
    CONSTRAINT fk_outbox_scan FOREIGN KEY (aggregate_id) REFERENCES scan_job(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
