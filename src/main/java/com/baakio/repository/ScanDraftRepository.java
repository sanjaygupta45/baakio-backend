package com.baakio.repository;

import com.baakio.model.StoredScanDraft;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ScanDraftRepository extends JpaRepository<StoredScanDraft, Long> {
    Optional<StoredScanDraft> findByScanJobId(long scanJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StoredScanDraft d where d.scanJobId = :scanJobId")
    Optional<StoredScanDraft> findLockedByScanJobId(@Param("scanJobId") long scanJobId);
}
