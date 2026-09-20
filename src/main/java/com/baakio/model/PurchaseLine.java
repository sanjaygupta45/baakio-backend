package com.baakio.model;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Table(name = "purchase_line")
@Getter
public class PurchaseLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "purchase_id", nullable = false)
    private long purchaseId;
    @Column(name = "line_no", nullable = false)
    private int lineNo;
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private java.math.BigDecimal quantity;
    @Column(name = "unit", nullable = true, length = 40)
    private String unit;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal unitPrice;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal lineTotal;

    protected PurchaseLine() {}

    public PurchaseLine(long purchaseId, int lineNo, String name, java.math.BigDecimal quantity, String unit, java.math.BigDecimal unitPrice, java.math.BigDecimal lineTotal) {
        this.purchaseId = purchaseId;
        this.lineNo = lineNo;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

}
