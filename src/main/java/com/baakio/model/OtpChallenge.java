package com.baakio.model;

import com.baakio.enums.OtpPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "otp_challenge")
@Getter
public class OtpChallenge {
    @Id
    @Column(name = "id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "phone_e164", nullable = false, length = 16)
    private String phoneE164;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 12)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "requested_at", nullable = false, insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected OtpChallenge() {}

    public OtpChallenge(String id, String phoneE164, OtpPurpose purpose, String codeHash, Instant expiresAt) {
        this.id = id;
        this.phoneE164 = phoneE164;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.failedAttempts = 0;
    }

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && invalidatedAt == null && expiresAt.isAfter(now);
    }

    public void recordFailedAttempt(Instant now, int maximumAttempts) {
        failedAttempts++;
        if (failedAttempts >= maximumAttempts) invalidatedAt = now;
    }

    public void consume(Instant now) { consumedAt = now; }
    public void invalidate(Instant now) { invalidatedAt = now; }
}
