package com.baakio.repository;

import com.baakio.model.AccountOpeningBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface AccountOpeningBalanceRepository extends JpaRepository<AccountOpeningBalance, Long> {
    boolean existsByBusinessIdAndCustomerId(String businessId, long customerId);

    Optional<AccountOpeningBalance> findByBusinessIdAndCustomerId(String businessId, long customerId);

    Optional<AccountOpeningBalance> findByBusinessIdAndCustomerIdAndId(String businessId, long customerId, long id);

    Optional<AccountOpeningBalance> findFirstByBusinessIdAndCustomerIdAndCutoffDateLessThanEqualOrderByCutoffDateDesc(
            String businessId, long customerId, LocalDate cutoffDate);

    @Query("select case when count(p) > 0 then true else false end from Purchase p where p.businessId = :businessId and p.customerId = :customerId and p.occurredOn <= :cutoffDate")
    boolean existsPurchaseOnOrBefore(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("cutoffDate") LocalDate cutoffDate);

    @Query("select case when count(p) > 0 then true else false end from Payment p where p.businessId = :businessId and p.customerId = :customerId and p.occurredOn <= :cutoffDate")
    boolean existsPaymentOnOrBefore(@Param("businessId") String businessId, @Param("customerId") long customerId,
            @Param("cutoffDate") LocalDate cutoffDate);
}
