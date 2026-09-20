package com.baakio.model;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Table(name = "payment_allocation")
@Getter
public class PaymentAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;
    @Column(name = "payment_id", nullable = false)
    private long paymentId;
    @Column(name = "commitment_id", nullable = false)
    private long commitmentId;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal amount;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant createdAt;

    protected PaymentAllocation() {}

    public PaymentAllocation(String businessId, long paymentId, long commitmentId, java.math.BigDecimal amount) {
        this.businessId = businessId;
        this.paymentId = paymentId;
        this.commitmentId = commitmentId;
        this.amount = amount;
    }

}
