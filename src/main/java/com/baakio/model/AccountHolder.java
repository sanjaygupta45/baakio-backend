package com.baakio.model;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "account_holder")
@Getter
public class AccountHolder {
    @Id
    @Column(name = "id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "phone_e164", nullable = true, length = 16)
    private String phoneE164;
    @Column(name = "email", nullable = true, length = 190)
    private String email;
    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;
    @Column(name = "preferred_locale", nullable = false, length = 8)
    private String preferredLocale;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AccountHolder() {}

    public AccountHolder(String id, String phoneE164, String email, String displayName, String preferredLocale) {
        this.id = id;
        this.phoneE164 = phoneE164;
        this.email = email;
        this.displayName = displayName;
        this.preferredLocale = preferredLocale;
        this.active = true;
    }

}
