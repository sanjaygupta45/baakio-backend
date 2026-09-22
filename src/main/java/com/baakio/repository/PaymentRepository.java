package com.baakio.repository;

import com.baakio.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("select p from Payment p where p.businessId = :businessId and p.customerId = :customerId and p.status = 'CONFIRMED' order by p.occurredOn desc, p.id desc")
    List<Payment> findConfirmedByCustomer(@Param("businessId") String businessId, @Param("customerId") long customerId);

    Optional<Payment> findByBusinessIdAndCustomerIdAndIdAndStatus(String businessId, long customerId, long id, String status);

    @Query("select coalesce(sum(p.amount), 0.00) from Payment p where p.businessId = :businessId and p.customerId = :customerId and p.status = 'CONFIRMED'")
    BigDecimal sumConfirmed(@Param("businessId") String businessId, @Param("customerId") long customerId);

    @Query("select coalesce(sum(p.amount), 0.00) from Payment p where p.sourcePurchaseId = :purchaseId and p.status = 'CONFIRMED'")
    BigDecimal sumForPurchase(@Param("purchaseId") long purchaseId);

    @Query("select coalesce(sum(p.amount), 0.00) from Payment p where p.businessId = :businessId and p.customerId = :customerId and p.status = 'CONFIRMED' and p.occurredOn between :fromDate and :toDate")
    BigDecimal sumBetween(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("select coalesce(sum(p.amount), 0.00) from Payment p where p.businessId = :businessId and p.customerId = :customerId and p.status = 'CONFIRMED' and p.occurredOn < :date")
    BigDecimal sumBefore(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("date") LocalDate date);

    @Query("select coalesce(sum(p.amount), 0.00) from Payment p where p.businessId = :businessId and p.customerId = :customerId and p.status = 'CONFIRMED' and p.occurredOn <= :date")
    BigDecimal sumBeforeOrOn(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("date") LocalDate date);

    @Query("select p from Payment p where p.businessId = :businessId and p.customerId = :customerId and p.status = 'CONFIRMED' and p.occurredOn between :fromDate and :toDate")
    List<Payment> findBetween(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
