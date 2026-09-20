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
import java.time.LocalDate;

@Entity
@Table(name = "payment_commitment")
@Getter
public class Commitment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;

    @Column(name = "customer_id", nullable = false)
    private long customerId;

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal originalAmount;

    @Setter
    @Column(name = "current_due_date", nullable = false)
    private LocalDate currentDueDate;

    @Setter
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "note", nullable = true, length = 500)
    private String note;

    @Column(name = "account_holder_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String accountHolderId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Commitment() {}

    public Commitment(String businessId, long customerId, BigDecimal originalAmount,
                      LocalDate currentDueDate, String status, String note, String accountHolderId) {
        this.businessId = businessId;
        this.customerId = customerId;
        this.originalAmount = originalAmount;
        this.currentDueDate = currentDueDate;
        this.status = status == null ? "ACTIVE" : status;
        this.note = note;
        this.accountHolderId = accountHolderId;
    }
}
