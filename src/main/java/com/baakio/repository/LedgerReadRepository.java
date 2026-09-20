package com.baakio.repository;

import com.baakio.model.AccountOpeningBalance;
import com.baakio.model.Payment;
import com.baakio.model.Purchase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class LedgerReadRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public long countPurchases(String businessId, long customerId, LocalDate from, LocalDate to) {
        var query = entityManager.createQuery("select count(p) from Purchase p where p.businessId = :businessId "
                + "and p.customerId = :customerId" + datePredicates(from, to), Long.class);
        query.setParameter("businessId", businessId).setParameter("customerId", customerId);
        applyDateRange(query, from, to);
        return query.getSingleResult();
    }

    public List<Long> findPurchaseIds(String businessId, long customerId, LocalDate from, LocalDate to,
                                     int size, int offset) {
        var query = entityManager.createQuery("select p.id from Purchase p where p.businessId = :businessId "
                + "and p.customerId = :customerId" + datePredicates(from, to)
                + " order by p.occurredOn desc, p.id desc", Long.class);
        query.setParameter("businessId", businessId).setParameter("customerId", customerId);
        applyDateRange(query, from, to);
        return query.setMaxResults(size).setFirstResult(offset).getResultList();
    }

    public List<Payment> findPayments(String businessId, long customerId) {
        return entityManager.createQuery("""
                select p from Payment p where p.businessId = :businessId and p.customerId = :customerId
                   and p.status = 'CONFIRMED' order by p.occurredOn desc, p.id desc
                """, Payment.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).getResultList();
    }

    public BigDecimal sumPurchasesBetween(String businessId, long customerId, LocalDate from, LocalDate to) {
        return sum("""
                select sum(p.total) from Purchase p where p.businessId = :businessId
                  and p.customerId = :customerId and p.occurredOn between :fromDate and :toDate
                """, businessId, customerId, from, to);
    }

    public BigDecimal sumPaymentsBetween(String businessId, long customerId, LocalDate from, LocalDate to) {
        return sum("""
                select sum(p.amount) from Payment p where p.businessId = :businessId
                  and p.customerId = :customerId and p.status = 'CONFIRMED'
                  and p.occurredOn between :fromDate and :toDate
                """, businessId, customerId, from, to);
    }

    public BigDecimal sumPurchasesBefore(String businessId, long customerId, LocalDate date) {
        return sum("""
                select sum(p.total) from Purchase p where p.businessId = :businessId
                  and p.customerId = :customerId and p.occurredOn < :toDate
                """, businessId, customerId, null, date);
    }

    public BigDecimal sumPaymentsBefore(String businessId, long customerId, LocalDate date) {
        return sum("""
                select sum(p.amount) from Payment p where p.businessId = :businessId
                  and p.customerId = :customerId and p.status = 'CONFIRMED' and p.occurredOn < :toDate
                """, businessId, customerId, null, date);
    }

    public AccountOpeningBalance findOpeningBalance(String businessId, long customerId) {
        return entityManager.createQuery("""
                select o from AccountOpeningBalance o where o.businessId = :businessId and o.customerId = :customerId
                """, AccountOpeningBalance.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).getResultList().stream().findFirst().orElse(null);
    }

    public List<Purchase> findPurchasesBetween(String businessId, long customerId, LocalDate from, LocalDate to) {
        return entityManager.createQuery("""
                select p from Purchase p where p.businessId = :businessId and p.customerId = :customerId
                   and p.occurredOn between :fromDate and :toDate
                """, Purchase.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).setParameter("fromDate", from)
                .setParameter("toDate", to).getResultList();
    }

    public List<Payment> findPaymentsBetween(String businessId, long customerId, LocalDate from, LocalDate to) {
        return entityManager.createQuery("""
                select p from Payment p where p.businessId = :businessId and p.customerId = :customerId
                   and p.status = 'CONFIRMED' and p.occurredOn between :fromDate and :toDate
                """, Payment.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).setParameter("fromDate", from)
                .setParameter("toDate", to).getResultList();
    }

    private String datePredicates(LocalDate from, LocalDate to) {
        StringBuilder predicates = new StringBuilder();
        if (from != null) predicates.append(" and p.occurredOn >= :fromDate");
        if (to != null) predicates.append(" and p.occurredOn <= :toDate");
        return predicates.toString();
    }

    private void applyDateRange(jakarta.persistence.TypedQuery<?> query, LocalDate from, LocalDate to) {
        if (from != null) {
            query.setParameter("fromDate", from);
        }
        if (to != null) {
            query.setParameter("toDate", to);
        }
    }

    private BigDecimal sum(String jpql, String businessId, long customerId, LocalDate from, LocalDate to) {
        var query = entityManager.createQuery(jpql, BigDecimal.class)
                .setParameter("businessId", businessId).setParameter("customerId", customerId);
        if (from != null) query.setParameter("fromDate", from);
        if (to != null) query.setParameter("toDate", to);
        BigDecimal total = query.getSingleResult();
        return total == null ? BigDecimal.ZERO.setScale(2) : total.setScale(2);
    }
}
