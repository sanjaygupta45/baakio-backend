package com.baakio.repository;

import com.baakio.model.Purchase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    Optional<Purchase> findByBusinessIdAndCustomerIdAndId(String businessId, long customerId, long id);

    @Query("select count(p) from Purchase p where p.businessId = :businessId and p.customerId = :customerId and (:fromDate is null or p.occurredOn >= :fromDate) and (:toDate is null or p.occurredOn <= :toDate)")
    long countForPeriod(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("select p.id from Purchase p where p.businessId = :businessId and p.customerId = :customerId and (:fromDate is null or p.occurredOn >= :fromDate) and (:toDate is null or p.occurredOn <= :toDate) order by p.occurredOn desc, p.id desc")
    List<Long> findIdsForPeriod(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, Pageable pageable);

    @Query("select coalesce(sum(p.total), 0.00) from Purchase p where p.businessId = :businessId and p.customerId = :customerId")
    BigDecimal sumTotal(@Param("businessId") String businessId, @Param("customerId") long customerId);

    @Query("select coalesce(sum(p.total), 0.00) from Purchase p where p.businessId = :businessId and p.customerId = :customerId and p.occurredOn between :fromDate and :toDate")
    BigDecimal sumBetween(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("select coalesce(sum(p.total), 0.00) from Purchase p where p.businessId = :businessId and p.customerId = :customerId and p.occurredOn < :date")
    BigDecimal sumBefore(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("date") LocalDate date);

    @Query("select coalesce(sum(p.total), 0.00) from Purchase p where p.businessId = :businessId and p.customerId = :customerId and p.occurredOn <= :date")
    BigDecimal sumBeforeOrOn(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("date") LocalDate date);

    @Query("select p from Purchase p where p.businessId = :businessId and p.customerId = :customerId and p.occurredOn between :fromDate and :toDate")
    List<Purchase> findBetween(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
