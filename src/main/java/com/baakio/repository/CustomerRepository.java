package com.baakio.repository;

import com.baakio.model.Customer;
import com.baakio.dto.customer.CommitmentSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class CustomerRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public long create(String businessId, String displayName, String phoneE164,
                       String notes, String preferredMessageLocale) {
        Customer customer = new Customer(businessId, displayName, phoneE164, notes, preferredMessageLocale);
        entityManager.persist(customer);
        entityManager.flush();
        return customer.getId();
    }

    public long countByBusinessAndSearch(String businessId, String search) {
        return entityManager.createQuery("""
                select count(c) from Customer c
                 where c.businessId = :businessId
                   and (:search = '' or lower(c.displayName) like :pattern
                        or lower(coalesce(c.phoneE164, '')) like :pattern)
                """, Long.class).setParameter("businessId", businessId).setParameter("search", search)
                .setParameter("pattern", "%" + search.toLowerCase() + "%").getSingleResult();
    }

    public List<Long> findIdsByBusinessAndSearch(String businessId, String search, int size, int offset) {
        return entityManager.createQuery("""
                select c.id from Customer c
                 where c.businessId = :businessId
                   and (:search = '' or lower(c.displayName) like :pattern
                        or lower(coalesce(c.phoneE164, '')) like :pattern)
                 order by c.displayName, c.id
                """, Long.class).setParameter("businessId", businessId).setParameter("search", search)
                .setParameter("pattern", "%" + search.toLowerCase() + "%")
                .setMaxResults(size).setFirstResult(offset).getResultList();
    }

    public Customer findByBusinessAndId(String businessId, long customerId) {
        return entityManager.createQuery("""
                select c from Customer c
                 where c.businessId = :businessId and c.id = :customerId
                """, Customer.class).setParameter("businessId", businessId).setParameter("customerId", customerId)
                .getResultList().stream().findFirst().orElse(null);
    }

    public boolean existsByBusinessAndId(String businessId, long customerId) {
        return entityManager.createQuery("select count(c) from Customer c where c.businessId = :businessId and c.id = :customerId", Long.class)
                .setParameter("businessId", businessId).setParameter("customerId", customerId).getSingleResult() > 0;
    }

    public boolean hasOpeningBalance(String businessId, long customerId) {
        return entityManager.createQuery("select count(o) from AccountOpeningBalance o where o.businessId = :businessId and o.customerId = :customerId", Long.class)
                .setParameter("businessId", businessId).setParameter("customerId", customerId).getSingleResult() > 0;
    }

    public CommitmentSummary findNextActiveCommitment(String businessId, long customerId, java.time.LocalDate today) {
        return entityManager.createQuery("""
                select new com.baakio.dto.customer.CommitmentSummary(
                    c.id, c.originalAmount - coalesce(sum(a.amount), 0.00), c.currentDueDate,
                    case when c.currentDueDate < :today then 'OVERDUE'
                         when c.currentDueDate = :today then 'DUE_TODAY'
                         else 'UPCOMING' end)
                  from Commitment c left join PaymentAllocation a on a.commitmentId = c.id
                 where c.businessId = :businessId and c.customerId = :customerId and c.status = 'ACTIVE'
                 group by c.id, c.originalAmount, c.currentDueDate
                having c.originalAmount - coalesce(sum(a.amount), 0.00) > 0
                 order by c.currentDueDate, c.id
                """, CommitmentSummary.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).setParameter("today", today)
                .setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    }
}
