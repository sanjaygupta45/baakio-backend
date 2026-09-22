package com.baakio.repository;

import com.baakio.dto.ledger.AllocationResponse;
import com.baakio.model.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    @Query("select c.originalAmount - coalesce(sum(a.amount), 0.00) from Commitment c left join PaymentAllocation a on a.commitmentId = c.id where c.businessId = :businessId and c.customerId = :customerId and c.id = :commitmentId and c.status = 'ACTIVE' group by c.id, c.originalAmount")
    Optional<BigDecimal> findRemaining(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("commitmentId") long commitmentId);

    @Query("""
            select new com.baakio.dto.ledger.AllocationResponse(
                a.commitmentId, a.amount,
                c.originalAmount - coalesce(sum(allocation.amount), 0.00))
              from PaymentAllocation a, Commitment c
              left join PaymentAllocation allocation on allocation.commitmentId = c.id
             where c.id = a.commitmentId and a.paymentId = :paymentId
             group by a.id, a.commitmentId, a.amount, c.originalAmount
            """)
    List<AllocationResponse> findResponsesByPaymentId(@Param("paymentId") long paymentId);
}
