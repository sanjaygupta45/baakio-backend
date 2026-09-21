package com.baakio.repository;

import com.baakio.model.ScanJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScanJobRepository extends JpaRepository<ScanJob, Long> {
    Optional<ScanJob> findByBusinessIdAndId(String businessId, Long id);
    Optional<ScanJob> findByBusinessIdAndUploadIdempotencyKey(String businessId, String uploadIdempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ScanJob j where j.id = :id")
    Optional<ScanJob> findLockedById(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ScanJob j where j.businessId = :businessId and j.id = :id")
    Optional<ScanJob> findLockedByBusinessIdAndId(@Param("businessId") String businessId,
                                                         @Param("id") long id);

    @Query(value = """
            select * from scan_job
             where status='PROCESSING' and locked_at < :cutoff
             order by locked_at limit 50 for update skip locked
            """, nativeQuery = true)
    List<ScanJob> lockStaleClaims(@Param("cutoff") Instant cutoff);

    List<ScanJob> findTop100ByKeepAttachmentFalseAndImageDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
            Instant cutoff);
}
