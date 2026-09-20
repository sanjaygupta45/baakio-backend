package com.baakio.model;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Table(name = "purchase")
@Getter
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;
    @Column(name = "customer_id", nullable = false)
    private long customerId;
    @Column(name = "occurred_on", nullable = false)
    private java.time.LocalDate occurredOn;
    @Column(name = "entry_mode", nullable = false, length = 12)
    private String entryMode;
    @Column(name = "description", nullable = true, length = 500)
    private String description;
    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal subtotal;
    @Column(name = "discount", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal discount;
    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal total;
    @Column(name = "account_holder_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String accountHolderId;
    @Column(name = "posted_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant postedAt;

    protected Purchase() {}

    public Purchase(String businessId, long customerId, java.time.LocalDate occurredOn, String entryMode, String description, java.math.BigDecimal subtotal, java.math.BigDecimal discount, java.math.BigDecimal total, String accountHolderId) {
        this.businessId = businessId;
        this.customerId = customerId;
        this.occurredOn = occurredOn;
        this.entryMode = entryMode;
        this.description = description;
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.accountHolderId = accountHolderId;
    }

}
