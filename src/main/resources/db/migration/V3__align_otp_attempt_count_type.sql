ALTER TABLE otp_challenge
    MODIFY COLUMN failed_attempts INT NOT NULL DEFAULT 0;
