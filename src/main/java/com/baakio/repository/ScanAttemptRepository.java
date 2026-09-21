package com.baakio.repository;

import com.baakio.model.ScanAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScanAttemptRepository extends JpaRepository<ScanAttempt, Long> {
    Optional<ScanAttempt> findByScanJobIdAndAttemptNo(long scanJobId, int attemptNo);
}
