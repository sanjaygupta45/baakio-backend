package com.baakio.model;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Table(name = "commitment_revision")
@Getter
public class CommitmentRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "commitment_id", nullable = false)
    private long commitmentId;
    @Column(name = "old_date", nullable = false)
    private java.time.LocalDate oldDate;
    @Column(name = "new_date", nullable = false)
    private java.time.LocalDate newDate;
    @Column(name = "reason", nullable = true, length = 500)
    private String reason;
    @Column(name = "account_holder_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String accountHolderId;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant createdAt;

    protected CommitmentRevision() {}

    public CommitmentRevision(long commitmentId, java.time.LocalDate oldDate, java.time.LocalDate newDate, String reason, String accountHolderId) {
        this.commitmentId = commitmentId;
        this.oldDate = oldDate;
        this.newDate = newDate;
        this.reason = reason;
        this.accountHolderId = accountHolderId;
    }

}
