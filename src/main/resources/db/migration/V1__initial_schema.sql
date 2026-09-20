CREATE TABLE business (
    id CHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Kolkata',
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    default_locale VARCHAR(8) NOT NULL DEFAULT 'en-IN',
    upi_vpa VARCHAR(120) NULL,
    upi_linked_mobile_e164 VARCHAR(20) NULL,
    upi_payee_name VARCHAR(160) NULL,
    upi_verified_by_owner_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_user (
    id CHAR(36) NOT NULL PRIMARY KEY,
    email VARCHAR(190) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    preferred_locale VARCHAR(8) NOT NULL DEFAULT 'en-IN',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE business_membership (
    business_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    role VARCHAR(16) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (business_id, user_id),
    CONSTRAINT fk_membership_business FOREIGN KEY (business_id) REFERENCES business(id),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE customer (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id CHAR(36) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    phone_e164 VARCHAR(20) NULL,
    notes VARCHAR(1000) NULL,
    preferred_message_locale VARCHAR(8) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_customer_business_name (business_id, display_name, id),
    KEY ix_customer_business_phone (business_id, phone_e164),
    CONSTRAINT fk_customer_business FOREIGN KEY (business_id) REFERENCES business(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_opening_balance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id CHAR(36) NOT NULL,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    cutoff_date DATE NOT NULL,
    posted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by CHAR(36) NOT NULL,
    UNIQUE KEY uq_opening_customer (business_id, customer_id),
    CONSTRAINT fk_opening_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_opening_user FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_opening_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id CHAR(36) NOT NULL,
    customer_id BIGINT NOT NULL,
    occurred_on DATE NOT NULL,
    entry_mode VARCHAR(12) NOT NULL,
    description VARCHAR(500) NULL,
    subtotal DECIMAL(19,2) NOT NULL,
    discount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    total DECIMAL(19,2) NOT NULL,
    posted_by CHAR(36) NOT NULL,
    posted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_purchase_account_date (business_id, customer_id, occurred_on, id),
    CONSTRAINT fk_purchase_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_purchase_user FOREIGN KEY (posted_by) REFERENCES app_user(id),
    CONSTRAINT ck_purchase_total CHECK (total > 0 AND discount >= 0 AND subtotal >= discount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_line (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    quantity DECIMAL(18,3) NOT NULL,
    unit VARCHAR(40) NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    line_total DECIMAL(19,2) NOT NULL,
    UNIQUE KEY uq_purchase_line_no (purchase_id, line_no),
    CONSTRAINT fk_line_purchase FOREIGN KEY (purchase_id) REFERENCES purchase(id),
    CONSTRAINT ck_purchase_line CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id CHAR(36) NOT NULL,
    customer_id BIGINT NOT NULL,
    source_purchase_id BIGINT NULL,
    amount DECIMAL(19,2) NOT NULL,
    method VARCHAR(12) NOT NULL,
    occurred_on DATE NOT NULL,
    reference_text VARCHAR(160) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'CONFIRMED',
    posted_by CHAR(36) NOT NULL,
    posted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_payment_source_purchase (source_purchase_id),
    KEY ix_payment_account_date (business_id, customer_id, occurred_on, id),
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_payment_purchase FOREIGN KEY (source_purchase_id) REFERENCES purchase(id),
    CONSTRAINT fk_payment_user FOREIGN KEY (posted_by) REFERENCES app_user(id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_commitment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id CHAR(36) NOT NULL,
    customer_id BIGINT NOT NULL,
    original_amount DECIMAL(19,2) NOT NULL,
    current_due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    note VARCHAR(500) NULL,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_commitment_account_status_date (business_id, customer_id, status, current_due_date, id),
    CONSTRAINT fk_commitment_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_commitment_user FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_commitment_amount CHECK (original_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commitment_revision (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    commitment_id BIGINT NOT NULL,
    old_date DATE NOT NULL,
    new_date DATE NOT NULL,
    reason VARCHAR(500) NULL,
    revised_by CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_revision_commitment (commitment_id, id),
    CONSTRAINT fk_revision_commitment FOREIGN KEY (commitment_id) REFERENCES payment_commitment(id),
    CONSTRAINT fk_revision_user FOREIGN KEY (revised_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_allocation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id CHAR(36) NOT NULL,
    payment_id BIGINT NOT NULL,
    commitment_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_payment_commitment (payment_id, commitment_id),
    KEY ix_allocation_commitment (business_id, commitment_id),
    CONSTRAINT fk_allocation_payment FOREIGN KEY (payment_id) REFERENCES payment(id),
    CONSTRAINT fk_allocation_commitment FOREIGN KEY (commitment_id) REFERENCES payment_commitment(id),
    CONSTRAINT ck_allocation_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_record (
    business_id CHAR(36) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT fk_idempotency_business FOREIGN KEY (business_id) REFERENCES business(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id CHAR(36) NOT NULL,
    actor_id CHAR(36) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_audit_business_entity (business_id, entity_type, entity_id, id),
    CONSTRAINT fk_audit_business FOREIGN KEY (business_id) REFERENCES business(id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
