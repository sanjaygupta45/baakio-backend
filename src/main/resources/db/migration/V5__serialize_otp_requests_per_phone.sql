-- A stable row lets concurrent OTP requests for the same phone serialize
-- before evaluating cooldown and hourly request limits.
CREATE TABLE otp_request_lock (
    phone_e164 VARCHAR(16) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
