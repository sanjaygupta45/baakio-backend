package com.baakio.repository;

import com.baakio.dto.customer.CommitmentSummary;
import com.baakio.model.Commitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CommitmentRepository extends JpaRepository<Commitment, Long> {
    @Query("select c.id from Commitment c where c.businessId = :businessId and c.customerId = :customerId order by c.currentDueDate, c.id")
    List<Long> findIds(@Param("businessId") String businessId, @Param("customerId") long customerId);

    Optional<Commitment> findByBusinessIdAndCustomerIdAndId(String businessId, long customerId, long id);

    Optional<Commitment> findByBusinessIdAndId(String businessId, long id);

    @Query("select c.originalAmount - coalesce(sum(a.amount), 0.00) from Commitment c left join PaymentAllocation a on a.commitmentId = c.id where c.id = :id group by c.id, c.originalAmount")
    Optional<BigDecimal> findRemainingAmount(@Param("id") long id);

    @Query("select c.originalAmount, coalesce(sum(a.amount), 0.00) from Commitment c left join PaymentAllocation a on a.commitmentId = c.id where c.businessId = :businessId and c.customerId = :customerId and c.status = 'ACTIVE' group by c.id, c.originalAmount")
    List<Object[]> findActiveAmounts(@Param("businessId") String businessId, @Param("customerId") long customerId);

    @Query("""
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
            """)
    List<CommitmentSummary> findActiveSummaries(@Param("businessId") String businessId,
            @Param("customerId") long customerId, @Param("today") java.time.LocalDate today);
}
