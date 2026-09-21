package com.baakio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "scan_draft")
@Getter
@Setter
public class StoredScanDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_job_id", nullable = false, unique = true)
    private long scanJobId;

    @Column(name = "extracted_json", nullable = false, columnDefinition = "LONGTEXT")
    private String extractedJson;

    @Column(name = "edited_json", nullable = false, columnDefinition = "LONGTEXT")
    private String editedJson;

    @Column(name = "written_total", precision = 19, scale = 2)
    private BigDecimal writtenTotal;

    @Column(name = "calculated_total", precision = 19, scale = 2)
    private BigDecimal calculatedTotal;

    @Column(name = "flags_json", nullable = false, columnDefinition = "LONGTEXT")
    private String flagsJson;

    @Column(name = "version", nullable = false)
    private long version = 1;

    @Column(name = "posted_purchase_id", unique = true)
    private Long postedPurchaseId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected StoredScanDraft() {}

    public StoredScanDraft(long scanJobId, String extractedJson, String editedJson,
                           BigDecimal writtenTotal, BigDecimal calculatedTotal, String flagsJson) {
        this.scanJobId = scanJobId;
        this.extractedJson = extractedJson;
        this.editedJson = editedJson;
        this.writtenTotal = writtenTotal;
        this.calculatedTotal = calculatedTotal;
        this.flagsJson = flagsJson;
    }
}
