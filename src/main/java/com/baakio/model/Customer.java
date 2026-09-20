package com.baakio.model;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Table(name = "customer")
@Getter
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;
    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;
    @Column(name = "phone_e164", nullable = true, length = 20)
    private String phoneE164;
    @Column(name = "notes", nullable = true, length = 1000)
    private String notes;
    @Column(name = "preferred_message_locale", nullable = true, length = 8)
    private String preferredMessageLocale;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant createdAt;

    protected Customer() {}

    public Customer(String businessId, String displayName, String phoneE164, String notes, String preferredMessageLocale) {
        this.businessId = businessId;
        this.displayName = displayName;
        this.phoneE164 = phoneE164;
        this.notes = notes;
        this.preferredMessageLocale = preferredMessageLocale;
    }

}
