package com.baakio.repository;

import com.baakio.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface BusinessRepository extends JpaRepository<Business, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Business b
               set b.upiVpa = :vpa,
                   b.upiLinkedMobileE164 = :mobile,
                   b.upiPayeeName = :payeeName,
                   b.paymentDetailsAccountHolderConfirmedAt = :confirmedAt
             where b.id = :businessId
            """)
    int updatePaymentDetails(@Param("businessId") String businessId, @Param("vpa") String vpa,
            @Param("mobile") String mobile, @Param("payeeName") String payeeName,
            @Param("confirmedAt") Instant confirmedAt);
}
