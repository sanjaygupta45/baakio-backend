package com.baakio.repository;

import com.baakio.model.EmailOtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailOtpChallengeRepository extends JpaRepository<EmailOtpChallenge, String> {
    @Modifying
    @Query(value = """
            INSERT INTO email_otp_global_lock (id) VALUES (1)
            ON DUPLICATE KEY UPDATE id = VALUES(id)
            """, nativeQuery = true)
    int acquireGlobalRequestLock();

    @Modifying
    @Query(value = """
            INSERT INTO email_otp_request_lock (email) VALUES (:email)
            ON DUPLICATE KEY UPDATE email = VALUES(email)
            """, nativeQuery = true)
    int acquireRequestLock(@Param("email") String email);

    long countByEmailAndRequestedAtGreaterThanEqual(String email, Instant since);
    long countByRequestedAtGreaterThanEqual(Instant since);
    Optional<EmailOtpChallenge> findFirstByEmailOrderByRequestedAtDesc(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailOtpChallenge c where c.id = :id")
    Optional<EmailOtpChallenge> findLockedById(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailOtpChallenge c set c.invalidatedAt = :now
             where c.email = :email and c.consumedAt is null and c.invalidatedAt is null
            """)
    int invalidatePending(@Param("email") String email, @Param("now") Instant now);
}
