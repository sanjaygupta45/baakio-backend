package com.baakio.repository;

import com.baakio.dto.ledger.AllocationResponse;
import com.baakio.model.AccountOpeningBalance;
import com.baakio.model.AuditEvent;
import com.baakio.model.Payment;
import com.baakio.model.PaymentAllocation;
import com.baakio.model.Commitment;
import com.baakio.model.Purchase;
import com.baakio.model.PurchaseLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class FinancialRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public boolean hasOpeningBalance(String businessId, long customerId) {
        return entityManager.createQuery("select count(o) from AccountOpeningBalance o where o.businessId = :businessId and o.customerId = :customerId", Long.class)
                .setParameter("businessId", businessId).setParameter("customerId", customerId).getSingleResult() > 0;
    }

    public boolean hasActivityOnOrBefore(String businessId, long customerId, LocalDate cutoffDate) {
        Long purchases = entityManager.createQuery("""
                select count(p) from Purchase p where p.businessId = :businessId
                  and p.customerId = :customerId and p.occurredOn <= :cutoffDate
                """, Long.class).setParameter("businessId", businessId).setParameter("customerId", customerId)
                .setParameter("cutoffDate", cutoffDate).getSingleResult();
        if (purchases > 0) return true;
        return entityManager.createQuery("""
                select count(p) from Payment p where p.businessId = :businessId
                  and p.customerId = :customerId and p.occurredOn <= :cutoffDate
                """, Long.class).setParameter("businessId", businessId).setParameter("customerId", customerId)
                .setParameter("cutoffDate", cutoffDate).getSingleResult() > 0;
    }

    @Transactional
    public long insertOpeningBalance(String businessId, long customerId, BigDecimal amount,
                                     LocalDate cutoffDate, String userId) {
        AccountOpeningBalance opening = new AccountOpeningBalance(businessId, customerId, amount, cutoffDate, userId);
        entityManager.persist(opening);
        entityManager.flush();
        return opening.getId();
    }

    @Transactional
    public long insertPurchase(Purchase purchase) {
        entityManager.persist(purchase);
        entityManager.flush();
        return purchase.getId();
    }

    @Transactional
    public void insertPurchaseLine(PurchaseLine line) {
        entityManager.persist(line);
    }

    @Transactional
    public long insertPayment(String businessId, long customerId, Long purchaseId, BigDecimal amount,
                              String method, LocalDate occurredOn, String reference, String userId) {
        Payment payment = new Payment(businessId, customerId, purchaseId, amount, method, occurredOn,
                reference, "CONFIRMED", userId);
        entityManager.persist(payment);
        entityManager.flush();
        return payment.getId();
    }

    public BigDecimal commitmentRemaining(String businessId, long customerId, long commitmentId) {
        List<BigDecimal> amounts = entityManager.createQuery("""
                select c.originalAmount - coalesce(sum(a.amount), 0.00)
                  from Commitment c left join PaymentAllocation a on a.commitmentId = c.id
                 where c.businessId = :businessId and c.customerId = :customerId
                   and c.id = :commitmentId and c.status = 'ACTIVE'
                 group by c.id, c.originalAmount
                """, BigDecimal.class).setParameter("businessId", businessId).setParameter("customerId", customerId)
                .setParameter("commitmentId", commitmentId).getResultList();
        return amounts.isEmpty() ? null : amounts.getFirst().setScale(2);
    }

    @Transactional
    public void insertAllocation(String businessId, long paymentId, long commitmentId, BigDecimal amount) {
        entityManager.persist(new PaymentAllocation(businessId, paymentId, commitmentId, amount));
    }

    @Transactional
    public void markCommitmentFulfilled(String businessId, long customerId, long commitmentId) {
        Commitment commitment = entityManager.createQuery("""
                select c from Commitment c where c.businessId = :businessId
                  and c.customerId = :customerId and c.id = :commitmentId
                """, Commitment.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).setParameter("commitmentId", commitmentId)
                .getResultList().stream().findFirst().orElse(null);
        if (commitment != null) commitment.setStatus("FULFILLED");
    }

    public AccountOpeningBalance findOpeningBalance(String businessId, long customerId, long openingId) {
        return entityManager.createQuery("""
                select o from AccountOpeningBalance o where o.businessId = :businessId
                   and o.customerId = :customerId and o.id = :openingId
                """, AccountOpeningBalance.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).setParameter("openingId", openingId)
                .getResultList().stream().findFirst().orElse(null);
    }

    public Purchase findPurchase(String businessId, long customerId, long purchaseId) {
        return entityManager.createQuery("""
                select p from Purchase p where p.businessId = :businessId
                   and p.customerId = :customerId and p.id = :purchaseId
                """, Purchase.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).setParameter("purchaseId", purchaseId)
                .getResultList().stream().findFirst().orElse(null);
    }

    public List<PurchaseLine> findPurchaseLines(long purchaseId) {
        return entityManager.createQuery("""
                select l from PurchaseLine l where l.purchaseId = :purchaseId order by l.lineNo
                """, PurchaseLine.class).setParameter("purchaseId", purchaseId).getResultList();
    }

    public BigDecimal purchasePaymentTotal(long purchaseId) {
        return money(entityManager.createQuery("""
                select sum(p.amount) from Payment p
                 where p.sourcePurchaseId = :purchaseId and p.status = 'CONFIRMED'
                """, BigDecimal.class).setParameter("purchaseId", purchaseId).getSingleResult());
    }

    public Payment findPayment(String businessId, long customerId, long paymentId) {
        return entityManager.createQuery("""
                select p from Payment p where p.businessId = :businessId and p.customerId = :customerId
                   and p.id = :paymentId and p.status = 'CONFIRMED'
                """, Payment.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).setParameter("paymentId", paymentId)
                .getResultList().stream().findFirst().orElse(null);
    }

    public List<AllocationResponse> findPaymentAllocations(long paymentId) {
        return entityManager.createQuery("""
                select new com.baakio.dto.ledger.AllocationResponse(
                    a.commitmentId, a.amount,
                    c.originalAmount - coalesce(sum(allocation.amount), 0.00))
                  from PaymentAllocation a, Commitment c
                  left join PaymentAllocation allocation on allocation.commitmentId = c.id
                 where c.id = a.commitmentId and a.paymentId = :paymentId
                 group by a.id, a.commitmentId, a.amount, c.originalAmount
                """, AllocationResponse.class).setParameter("paymentId", paymentId).getResultList();
    }

    public LocalDate findOpeningCutoff(String businessId, long customerId) {
        return entityManager.createQuery("""
                select o.cutoffDate from AccountOpeningBalance o
                 where o.businessId = :businessId and o.customerId = :customerId
                """, LocalDate.class).setParameter("businessId", businessId).setParameter("customerId", customerId)
                .getResultList().stream().findFirst().orElse(null);
    }

    @Transactional
    public void audit(String businessId, String userId, String entityType, long entityId, String action) {
        entityManager.persist(new AuditEvent(businessId, userId, entityType, entityId, action));
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }
}
