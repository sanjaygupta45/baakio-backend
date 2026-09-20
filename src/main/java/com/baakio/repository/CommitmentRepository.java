package com.baakio.repository;

import com.baakio.model.AuditEvent;
import com.baakio.model.CommitmentRevision;
import com.baakio.model.Commitment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class CommitmentRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public long create(String businessId, long customerId, BigDecimal amount, LocalDate dueDate,
                       String note, String userId) {
        Commitment commitment = new Commitment(businessId, customerId, amount, dueDate,
                "ACTIVE", note, userId);
        entityManager.persist(commitment);
        entityManager.flush();
        return commitment.getId();
    }

    @Transactional
    public void addRevisionAndUpdate(String businessId, long commitmentId, LocalDate oldDate,
                                     LocalDate newDate, String reason, String userId) {
        Commitment commitment = entityManager.createQuery("""
                select c from Commitment c
                 where c.businessId = :businessId and c.id = :commitmentId
                """, Commitment.class).setParameter("businessId", businessId)
                .setParameter("commitmentId", commitmentId).getResultList().stream().findFirst().orElse(null);
        if (commitment == null) return;
        entityManager.persist(new CommitmentRevision(commitmentId, oldDate, newDate, reason, userId));
        commitment.setCurrentDueDate(newDate);
    }

    public List<Long> findIds(String businessId, long customerId) {
        return entityManager.createQuery("""
                select c.id from Commitment c
                 where c.businessId = :businessId and c.customerId = :customerId
                 order by c.currentDueDate, c.id
                """, Long.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).getResultList();
    }

    public Commitment findByBusinessCustomerAndId(String businessId, long customerId, long id) {
        return entityManager.createQuery("""
                select c from Commitment c
                 where c.businessId = :businessId and c.customerId = :customerId and c.id = :id
                """, Commitment.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).setParameter("id", id)
                .getResultList().stream().findFirst().orElse(null);
    }

    public BigDecimal findRemainingAmount(long id) {
        return entityManager.createQuery("""
                select c.originalAmount - coalesce(sum(a.amount), 0.00)
                  from Commitment c left join PaymentAllocation a on a.commitmentId = c.id
                 where c.id = :id group by c.id, c.originalAmount
                """, BigDecimal.class).setParameter("id", id)
                .getResultList().stream().findFirst().orElse(BigDecimal.ZERO);
    }

    public Commitment findReference(String businessId, long id) {
        return entityManager.createQuery("""
                select c from Commitment c where c.businessId = :businessId and c.id = :id
                """, Commitment.class).setParameter("businessId", businessId)
                .setParameter("id", id).getResultList().stream().findFirst().orElse(null);
    }

    public List<CommitmentRevision> findRevisions(long commitmentId) {
        return entityManager.createQuery("""
                select r from CommitmentRevision r where r.commitmentId = :commitmentId order by r.id
                """, CommitmentRevision.class).setParameter("commitmentId", commitmentId).getResultList();
    }

    @Transactional
    public void audit(String businessId, String userId, long commitmentId, String action) {
        entityManager.persist(new AuditEvent(businessId, userId, "COMMITMENT", commitmentId, action));
    }
}
