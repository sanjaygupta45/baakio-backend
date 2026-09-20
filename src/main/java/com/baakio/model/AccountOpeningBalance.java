package com.baakio.model;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Table(name = "account_opening_balance")
@Getter
public class AccountOpeningBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;
    @Column(name = "customer_id", nullable = false)
    private long customerId;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal amount;
    @Column(name = "cutoff_date", nullable = false)
    private java.time.LocalDate cutoffDate;
    @Column(name = "posted_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant postedAt;
    @Column(name = "account_holder_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String accountHolderId;

    protected AccountOpeningBalance() {}

    public AccountOpeningBalance(String businessId, long customerId, java.math.BigDecimal amount, java.time.LocalDate cutoffDate, String accountHolderId) {
        this.businessId = businessId;
        this.customerId = customerId;
        this.amount = amount;
        this.cutoffDate = cutoffDate;
        this.accountHolderId = accountHolderId;
    }

}
