package com.baakio.service.auth;

import com.baakio.dto.auth.OtpRequestResponse;
import com.baakio.enums.EmailOtpPurpose;
import com.baakio.enums.NotificationChannel;
import com.baakio.exception.ApiException;
import com.baakio.model.EmailOtpChallenge;
import com.baakio.notification.NotificationMessage;
import com.baakio.notification.service.NotificationDispatcher;
import com.baakio.repository.AccountHolderRepository;
import com.baakio.repository.AccountIdentityRepository;
import com.baakio.repository.EmailOtpChallengeRepository;
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
public class EmailOtpService {
    private final EmailOtpChallengeRepository challenges;
    private final AccountHolderRepository accountHolders;
    private final AccountIdentityRepository identities;
    private final OtpCodeGenerator codeGenerator;
    private final NotificationDispatcher notifications;
    private final String hashSecret;
    private final boolean enabled;
    private final boolean exposeDevelopmentCode;
    private final int expiresSeconds;
    private final int cooldownSeconds;
    private final int hourlyLimit;
    private final int dailyLimit;
    private final int attemptLimit;

    public EmailOtpService(EmailOtpChallengeRepository challenges, AccountHolderRepository accountHolders,
            AccountIdentityRepository identities, OtpCodeGenerator codeGenerator,
            NotificationDispatcher notifications,
            @Value("${baakio.auth.otp.hash-secret}") String hashSecret,
            @Value("${baakio.auth.email.enabled}") boolean enabled,
            @Value("${baakio.auth.otp.expose-development-code}") boolean exposeDevelopmentCode,
            @Value("${baakio.auth.otp.expires-seconds}") int expiresSeconds,
            @Value("${baakio.auth.otp.resend-cooldown-seconds}") int cooldownSeconds,
            @Value("${baakio.auth.otp.max-requests-per-hour}") int hourlyLimit,
            @Value("${baakio.auth.email.max-requests-per-day:80}") int dailyLimit,
            @Value("${baakio.auth.otp.max-attempts}") int attemptLimit) {
        this.challenges = challenges;
        this.accountHolders = accountHolders;
        this.identities = identities;
        this.codeGenerator = codeGenerator;
        this.notifications = notifications;
        this.hashSecret = hashSecret;
        this.enabled = enabled;
        this.exposeDevelopmentCode = exposeDevelopmentCode;
        this.expiresSeconds = expiresSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.hourlyLimit = hourlyLimit;
        this.dailyLimit = dailyLimit;
        this.attemptLimit = attemptLimit;
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public OtpRequestResponse request(String rawEmail, EmailOtpPurpose purpose, String locale) {
        requireEnabled();
        String email = normalize(rawEmail);
        if (purpose == EmailOtpPurpose.REGISTER
                && (accountHolders.existsByEmailIgnoreCase(email)
                || identities.accountHolderId("EMAIL", email).isPresent())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "An account already exists with this email. Sign in instead.");
        }
        if (purpose == EmailOtpPurpose.LOGIN && identities.accountHolderId("EMAIL", email).isEmpty()
                && accountHolders.existsByEmailIgnoreCase(email)) {
            throw emailSignInNotLinked();
        }
        challenges.acquireGlobalRequestLock();
        challenges.acquireRequestLock(email);
        Instant now = Instant.now();
        if (challenges.countByRequestedAtGreaterThanEqual(now.minus(Duration.ofDays(1))) >= dailyLimit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_DAILY_LIMIT",
                    "Email sign-in is temporarily unavailable. Try again later.");
        }
        if (challenges.countByEmailAndRequestedAtGreaterThanEqual(email,
                now.minus(Duration.ofHours(1))) >= hourlyLimit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                    "Too many code requests. Try again later.");
        }
        EmailOtpChallenge latest = challenges.findFirstByEmailOrderByRequestedAtDesc(email).orElse(null);
        if (latest != null && latest.getRequestedAt() != null
                && Duration.between(latest.getRequestedAt(), now).toSeconds() < cooldownSeconds) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RESEND_TOO_SOON",
                    "Wait before requesting another code.");
        }
        String id = UUID.randomUUID().toString();
        String code = codeGenerator.generate();
        challenges.invalidatePending(email, now);
        challenges.saveAndFlush(new EmailOtpChallenge(id, email, purpose,
                hash(id, email, code), now.plusSeconds(expiresSeconds)));
        notifications.send(NotificationChannel.EMAIL,
                NotificationMessage.emailOtp(email, code, locale, id, expiresSeconds, purpose));
        return new OtpRequestResponse(id, expiresSeconds, cooldownSeconds,
                exposeDevelopmentCode ? code : null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consume(String challengeId, String rawEmail, EmailOtpPurpose purpose, String code) {
        requireEnabled();
        if (challengeId == null || rawEmail == null || code == null) return false;
        String email = normalize(rawEmail);
        EmailOtpChallenge challenge = challenges.findLockedById(challengeId).orElse(null);
        Instant now = Instant.now();
        if (challenge == null || !challenge.getEmail().equals(email) || challenge.getPurpose() != purpose
                || !challenge.isUsableAt(now)) return false;
        if (!MessageDigest.isEqual(challenge.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(challengeId, email, code).getBytes(StandardCharsets.US_ASCII))) {
            challenge.recordFailedAttempt(now, attemptLimit);
            return false;
        }
        challenge.consume(now);
        return true;
    }

    private void requireEnabled() {
        if (!enabled) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "EMAIL_AUTH_NOT_CONFIGURED", "Email sign-in is not configured.");
    }

    private ApiException emailSignInNotLinked() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_SIGN_IN_NOT_LINKED",
                "This account does not use email sign-in. Sign in using its current method, or link this email in settings.");
    }

    private String hash(String id, String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    ("email:" + id + ":" + email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("OTP hashing could not be initialized.", exception);
        }
    }
}
