package com.baakio.repository;

import com.baakio.dto.customer.CommitmentSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDate;

@Repository
public class DashboardRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public List<Long> findCustomerIds(String businessId) {
        return entityManager.createQuery("""
                select c.id from Customer c where c.businessId = :businessId
                 order by c.createdAt desc, c.id desc
                """, Long.class).setParameter("businessId", businessId).getResultList();
    }

    public List<CommitmentSummary> findActivePromises(String businessId, long customerId, LocalDate today) {
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
                """, CommitmentSummary.class).setParameter("businessId", businessId)
                .setParameter("customerId", customerId).setParameter("today", today).getResultList();
    }
}
