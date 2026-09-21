package com.baakio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "scan_attempt")
@Getter
@Setter
public class ScanAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_job_id", nullable = false)
    private long scanJobId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "started_at", nullable = false, insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ScanAttempt() {}

    public ScanAttempt(long scanJobId, int attemptNo, String status) {
        this.scanJobId = scanJobId;
        this.attemptNo = attemptNo;
        this.status = status;
    }
}
