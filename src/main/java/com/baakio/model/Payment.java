package com.baakio.model;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Table(name = "payment")
@Getter
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;
    @Column(name = "customer_id", nullable = false)
    private long customerId;
    @Column(name = "source_purchase_id", nullable = true)
    private Long sourcePurchaseId;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal amount;
    @Column(name = "method", nullable = false, length = 12)
    private String method;
    @Column(name = "occurred_on", nullable = false)
    private java.time.LocalDate occurredOn;
    @Column(name = "reference_text", nullable = true, length = 160)
    private String referenceText;
    @Column(name = "status", nullable = false, length = 24)
    private String status;
    @Column(name = "account_holder_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String accountHolderId;
    @Column(name = "posted_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant postedAt;

    protected Payment() {}

    public Payment(String businessId, long customerId, Long sourcePurchaseId, java.math.BigDecimal amount, String method, java.time.LocalDate occurredOn, String referenceText, String status, String accountHolderId) {
        this.businessId = businessId;
        this.customerId = customerId;
        this.sourcePurchaseId = sourcePurchaseId;
        this.amount = amount;
        this.method = method;
        this.occurredOn = occurredOn;
        this.referenceText = referenceText;
        this.status = status;
        this.accountHolderId = accountHolderId;
        if (this.status == null) this.status = "CONFIRMED";
    }

}
