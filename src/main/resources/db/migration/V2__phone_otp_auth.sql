ALTER TABLE app_user
    MODIFY COLUMN email VARCHAR(190) NULL,
    MODIFY COLUMN password_hash VARCHAR(100) NULL,
    ADD COLUMN phone_e164 VARCHAR(16) NULL,
    ADD UNIQUE KEY uq_app_user_phone_e164 (phone_e164);

CREATE TABLE otp_challenge (
    id CHAR(36) NOT NULL PRIMARY KEY,
    phone_e164 VARCHAR(16) NOT NULL,
    purpose VARCHAR(12) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    failed_attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
    consumed_at DATETIME(6) NULL,
    invalidated_at DATETIME(6) NULL,
    KEY ix_otp_phone_requested (phone_e164, requested_at),
    CONSTRAINT ck_otp_purpose CHECK (purpose IN ('LOGIN', 'REGISTER')),
    CONSTRAINT ck_otp_attempts CHECK (failed_attempts <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
