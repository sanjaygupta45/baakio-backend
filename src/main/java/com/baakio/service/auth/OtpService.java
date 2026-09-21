package com.baakio.service.auth;

import com.baakio.dto.auth.OtpRequestResponse;
import com.baakio.model.OtpChallenge;
import com.baakio.enums.OtpPurpose;
import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import com.baakio.notification.service.NotificationDispatcher;
import com.baakio.repository.OtpChallengeRepository;
import com.baakio.exception.ApiException;
import com.baakio.utils.IndianPhoneNumbers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class OtpService {
    private final OtpChallengeRepository challenges;
    private final OtpCodeGenerator codeGenerator;
    private final NotificationDispatcher notifications;
    private final String hashSecret;
    private final int expiresInSeconds;
    private final int resendCooldownSeconds;
    private final int maxRequestsPerHour;
    private final int maxAttempts;
    private final boolean exposeDevelopmentCode;
    private final NotificationChannel channel;

    public OtpService(OtpChallengeRepository challenges, OtpCodeGenerator codeGenerator,
            NotificationDispatcher notifications,
            @Value("${baakio.auth.otp.hash-secret}") String hashSecret,
            @Value("${baakio.auth.otp.expires-seconds}") int expiresInSeconds,
            @Value("${baakio.auth.otp.resend-cooldown-seconds}") int resendCooldownSeconds,
            @Value("${baakio.auth.otp.max-requests-per-hour}") int maxRequestsPerHour,
            @Value("${baakio.auth.otp.max-attempts}") int maxAttempts,
            @Value("${baakio.auth.otp.expose-development-code}") boolean exposeDevelopmentCode,
            @Value("${baakio.notifications.otp-channel}") NotificationChannel channel) {
        if (hashSecret == null || hashSecret.length() < 32) {
            throw new IllegalStateException("Configure OTP_HASH_SECRET with at least 32 characters.");
        }
        if (expiresInSeconds < 1 || expiresInSeconds > 10_080 * 60) {
            throw new IllegalStateException("OTP_EXPIRES_SECONDS must be between 1 and 604800.");
        }
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalStateException("OTP_MAX_ATTEMPTS must be between 1 and 5.");
        }
        if (resendCooldownSeconds < 0 || maxRequestsPerHour < 1) {
            throw new IllegalStateException("OTP resend cooldown cannot be negative and the hourly request limit must be positive.");
        }
        this.challenges = challenges;
        this.codeGenerator = codeGenerator;
        this.notifications = notifications;
        this.hashSecret = hashSecret;
        this.expiresInSeconds = expiresInSeconds;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxRequestsPerHour = maxRequestsPerHour;
        this.maxAttempts = maxAttempts;
        this.exposeDevelopmentCode = exposeDevelopmentCode;
        this.channel = channel;
    }

    @Transactional
    public OtpRequestResponse request(String rawPhone, OtpPurpose purpose, String locale) {
        String phone = IndianPhoneNumbers.normalize(rawPhone);
        // The upsert holds a row lock until this transaction commits, so requests
        // for this phone cannot race past the cooldown or hourly rate limit.
        challenges.acquirePhoneRequestLock(phone);
        Instant now = Instant.now();
        if (challenges.countByPhoneE164AndRequestedAtGreaterThanEqual(phone,
                now.minus(Duration.ofHours(1))) >= maxRequestsPerHour) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                    "Too many code requests. Try again later.");
        }
        OtpChallenge latest = challenges.findFirstByPhoneE164OrderByRequestedAtDesc(phone).orElse(null);
        if (latest != null && latest.getRequestedAt() != null) {
            long remaining = resendCooldownSeconds - Duration.between(latest.getRequestedAt(), now).toSeconds();
            if (remaining > 0) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RESEND_TOO_SOON",
                        "Wait " + remaining + " seconds before requesting another code.");
            }
        }
        String id = UUID.randomUUID().toString();
        String code = codeGenerator.generate();
        String hash = hash(id, phone, code);
        challenges.invalidatePending(phone, now);
        challenges.saveAndFlush(new OtpChallenge(id, phone, purpose, hash, now.plusSeconds(expiresInSeconds)));
        notifications.send(channel, NotificationMessage.otp(phone, code, locale, id, expiresInSeconds));
        return new OtpRequestResponse(id, expiresInSeconds, resendCooldownSeconds,
                exposeDevelopmentCode ? code : null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consume(String challengeId, String rawPhone, OtpPurpose purpose, String code) {
        if (challengeId == null || rawPhone == null || purpose == null || code == null) return false;
        String phone;
        try {
            phone = IndianPhoneNumbers.normalize(rawPhone);
        } catch (RuntimeException exception) {
            return false;
        }
        OtpChallenge challenge = challenges.findLockedById(challengeId).orElse(null);
        Instant now = Instant.now();
        if (challenge == null || !challenge.getPhoneE164().equals(phone) || challenge.getPurpose() != purpose) return false;
        if (!challenge.isUsableAt(now)) {
            if (challenge.getConsumedAt() == null && challenge.getInvalidatedAt() == null) challenge.invalidate(now);
            return false;
        }
        if (!MessageDigest.isEqual(challenge.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(challengeId, phone, code).getBytes(StandardCharsets.US_ASCII))) {
            challenge.recordFailedAttempt(now, maxAttempts);
            return false;
        }
        challenge.consume(now);
        return true;
    }

    private String hash(String challengeId, String phone, String code) {
        if (hashSecret == null || hashSecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OTP_NOT_CONFIGURED",
                    "OTP authentication is not configured.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((challengeId + ":" + phone + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("OTP hashing could not be initialized.", exception);
        }
    }
}
