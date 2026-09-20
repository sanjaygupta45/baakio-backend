package com.baakio.model;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Table(name = "audit_event")
@Getter
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;
    @Column(name = "account_holder_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String accountHolderId;
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private long entityId;
    @Column(name = "action", nullable = false, length = 40)
    private String action;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(String businessId, String accountHolderId, String entityType, long entityId, String action) {
        this.businessId = businessId;
        this.accountHolderId = accountHolderId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
    }

}
