package com.baakio.repository;

import com.baakio.model.Business;
import com.baakio.model.Customer;
import com.baakio.exception.ApiException;
import com.baakio.utils.MoneyAmounts;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Repository
public class LedgerRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public void lockCustomer(String businessId, long customerId) {
        List<Customer> matches = entityManager.createQuery("""
                select c from Customer c where c.businessId = :businessId and c.id = :customerId
                """, Customer.class).setParameter("businessId", businessId).setParameter("customerId", customerId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if (matches.isEmpty()) throw ApiException.notFound();
    }

    public BigDecimal outstanding(String businessId, long customerId) {
        // Promises are not money; only the opening balance, purchases, and confirmed payments count here.
        BigDecimal opening = opening(businessId, customerId);
        BigDecimal purchases = totalPurchases(businessId, customerId);
        BigDecimal payments = totalPaid(businessId, customerId);
        return opening.add(purchases).subtract(payments).setScale(2);
    }

    public BigDecimal totalPurchases(String businessId, long customerId) {
        return total("select sum(p.total) from Purchase p where p.businessId = :businessId and p.customerId = :customerId",
                businessId, customerId);
    }

    public BigDecimal totalPaid(String businessId, long customerId) {
        return total("select sum(p.amount) from Payment p where p.businessId = :businessId "
                        + "and p.customerId = :customerId and p.status = 'CONFIRMED'",
                businessId, customerId);
    }

    public BigDecimal outstandingThrough(String businessId, long customerId, LocalDate date) {
        BigDecimal opening = amount("""
                select max(o.amount) from AccountOpeningBalance o where o.businessId = :businessId
                  and o.customerId = :customerId and o.cutoffDate <= :date
                """, businessId, customerId, date);
        BigDecimal purchases = amount("""
                select sum(p.total) from Purchase p where p.businessId = :businessId
                  and p.customerId = :customerId and p.occurredOn <= :date
                """, businessId, customerId, date);
        BigDecimal payments = amount("""
                select sum(p.amount) from Payment p where p.businessId = :businessId
                  and p.customerId = :customerId and p.status = 'CONFIRMED' and p.occurredOn <= :date
                """, businessId, customerId, date);
        return opening.add(purchases).subtract(payments).setScale(2);
    }

    public BigDecimal opening(String businessId, long customerId) {
        return amount("""
                select max(o.amount) from AccountOpeningBalance o
                 where o.businessId = :businessId and o.customerId = :customerId
                """, businessId, customerId, null);
    }

    public BigDecimal activeCommitmentRemaining(String businessId, long customerId) {
        List<Object[]> rows = entityManager.createQuery("""
                select c.originalAmount, coalesce(sum(a.amount), 0.00)
                  from Commitment c left join PaymentAllocation a on a.commitmentId = c.id
                 where c.businessId = :businessId and c.customerId = :customerId and c.status = 'ACTIVE'
                 group by c.id, c.originalAmount
                """, Object[].class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).getResultList();
        BigDecimal remaining = MoneyAmounts.zero();
        for (Object[] row : rows) {
            remaining = remaining.add((BigDecimal) row[0]).subtract((BigDecimal) row[1]);
        }
        return remaining.setScale(2);
    }

    public LocalDate businessDate(String businessId) {
        Business business = entityManager.find(Business.class, businessId);
        try {
            return LocalDate.now(ZoneId.of(business == null ? null : business.getTimezone()));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BUSINESS_TIMEZONE_INVALID",
                    "The shop time zone is not configured correctly.");
        }
    }

    private BigDecimal total(String jpql, String businessId, long customerId) {
        var query = entityManager.createQuery(jpql, BigDecimal.class)
                .setParameter("businessId", businessId).setParameter("customerId", customerId);
        return normalize(query.getSingleResult());
    }

    private BigDecimal amount(String jpql, String businessId, long customerId, LocalDate date) {
        var query = entityManager.createQuery(jpql, BigDecimal.class)
                .setParameter("businessId", businessId).setParameter("customerId", customerId);
        if (date != null) query.setParameter("date", date);
        return normalize(query.getSingleResult());
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? MoneyAmounts.zero() : value.setScale(2);
    }
}
