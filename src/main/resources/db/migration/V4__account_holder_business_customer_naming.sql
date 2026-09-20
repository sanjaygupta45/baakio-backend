-- Each business currently has one active account holder. Do not discard legacy
-- access rows silently: the NOT NULL change below fails if that rule is not met.
ALTER TABLE business
    ADD COLUMN account_holder_id CHAR(36) NULL;

UPDATE business b
LEFT JOIN (
    SELECT business_id,
           MIN(user_id) AS account_holder_id,
           COUNT(*) AS membership_count,
           SUM(CASE WHEN role = 'OWNER' AND active = TRUE THEN 1 ELSE 0 END) AS active_owner_count
      FROM business_membership
     GROUP BY business_id
) membership ON membership.business_id = b.id
   SET b.account_holder_id = CASE
       WHEN membership.membership_count = 1 AND membership.active_owner_count = 1
       THEN membership.account_holder_id
       ELSE NULL
   END;

ALTER TABLE business
    MODIFY COLUMN account_holder_id CHAR(36) NOT NULL;

RENAME TABLE app_user TO account_holder;

ALTER TABLE account_holder
    DROP INDEX uq_app_user_email,
    ADD UNIQUE KEY uq_account_holder_email (email),
    DROP INDEX uq_app_user_phone_e164,
    ADD UNIQUE KEY uq_account_holder_phone_e164 (phone_e164);

ALTER TABLE business
    RENAME COLUMN upi_verified_by_owner_at TO upi_verified_by_account_holder_at;

ALTER TABLE business
    ADD KEY ix_business_account_holder (account_holder_id),
    ADD CONSTRAINT fk_business_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holder(id);

ALTER TABLE account_opening_balance
    DROP FOREIGN KEY fk_opening_user,
    CHANGE COLUMN created_by account_holder_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_opening_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holder(id);

ALTER TABLE purchase
    DROP FOREIGN KEY fk_purchase_user,
    CHANGE COLUMN posted_by account_holder_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_purchase_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holder(id);

ALTER TABLE payment
    DROP FOREIGN KEY fk_payment_user,
    CHANGE COLUMN posted_by account_holder_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_payment_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holder(id);

ALTER TABLE payment_commitment
    DROP FOREIGN KEY fk_commitment_user,
    CHANGE COLUMN created_by account_holder_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_commitment_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holder(id);

ALTER TABLE commitment_revision
    DROP FOREIGN KEY fk_revision_user,
    CHANGE COLUMN revised_by account_holder_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_revision_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holder(id);

ALTER TABLE audit_event
    DROP FOREIGN KEY fk_audit_actor,
    CHANGE COLUMN actor_id account_holder_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_audit_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holder(id);

DROP TABLE business_membership;
