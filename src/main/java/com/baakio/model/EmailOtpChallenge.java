package com.baakio.model;

import com.baakio.enums.EmailOtpPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "email_otp_challenge")
@Getter
public class EmailOtpChallenge {
    @Id
    @Column(name = "id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "email", nullable = false, length = 190)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 12)
    private EmailOtpPurpose purpose;
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected EmailOtpChallenge() {}
    public EmailOtpChallenge(String id, String email, EmailOtpPurpose purpose, String codeHash, Instant expiresAt) {
        this.id = id;
        this.email = email;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.requestedAt = Instant.now();
        this.expiresAt = expiresAt;
    }
    public boolean isUsableAt(Instant now) {
        return consumedAt == null && invalidatedAt == null && expiresAt.isAfter(now);
    }
    public void recordFailedAttempt(Instant now, int limit) {
        failedAttempts++;
        if (failedAttempts >= limit) invalidatedAt = now;
    }
    public void consume(Instant now) { consumedAt = now; }
}
