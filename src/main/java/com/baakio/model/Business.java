package com.baakio.model;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

@Entity
@Table(name = "business")
@Getter
public class Business {
    @Id
    @Column(name = "id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "account_holder_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String accountHolderId;
    @Column(name = "timezone", nullable = false, length = 80)
    private String timezone;
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;
    @Column(name = "default_locale", nullable = false, length = 8)
    @Setter
    private String defaultLocale;
    @Column(name = "upi_vpa", nullable = true, length = 120)
    private String upiVpa;
    @Column(name = "upi_linked_mobile_e164", nullable = true, length = 20)
    private String upiLinkedMobileE164;
    @Column(name = "upi_payee_name", nullable = true, length = 160)
    private String upiPayeeName;
    @Column(name = "upi_verified_by_account_holder_at", nullable = true)
    private java.time.Instant paymentDetailsAccountHolderConfirmedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant createdAt;

    protected Business() {}

    public Business(String id, String name, String accountHolderId) {
        this.id = id;
        this.name = name;
        this.accountHolderId = accountHolderId;
        this.timezone = "Asia/Kolkata";
        this.currency = "INR";
        this.defaultLocale = "en-IN";
    }

}
