package com.baakio.repository;

import com.baakio.model.OtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {

    @Modifying
    @Query(value = """
            INSERT INTO otp_request_lock (phone_e164) VALUES (:phone)
            ON DUPLICATE KEY UPDATE phone_e164 = VALUES(phone_e164)
            """, nativeQuery = true)
    int acquirePhoneRequestLock(@Param("phone") String phoneE164);

    long countByPhoneE164AndRequestedAtGreaterThanEqual(String phoneE164, Instant since);

    Optional<OtpChallenge> findFirstByPhoneE164OrderByRequestedAtDesc(String phoneE164);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtpChallenge c where c.id = :id")
    Optional<OtpChallenge> findLockedById(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OtpChallenge c set c.invalidatedAt = :now
             where c.phoneE164 = :phone and c.consumedAt is null and c.invalidatedAt is null
            """)
    int invalidatePending(@Param("phone") String phoneE164, @Param("now") Instant now);
}
