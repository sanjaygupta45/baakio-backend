package com.baakio.repository;

import com.baakio.model.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("""
            select count(c) from Customer c
             where c.businessId = :businessId
               and (:search = '' or lower(c.displayName) like :pattern
                    or lower(coalesce(c.phoneE164, '')) like :pattern)
            """)
    long countByBusinessAndSearch(@Param("businessId") String businessId,
            @Param("search") String search, @Param("pattern") String pattern);

    @Query("""
            select c.id from Customer c
             where c.businessId = :businessId
               and (:search = '' or lower(c.displayName) like :pattern
                    or lower(coalesce(c.phoneE164, '')) like :pattern)
             order by c.displayName, c.id
            """)
    List<Long> findIdsByBusinessAndSearch(@Param("businessId") String businessId,
            @Param("search") String search, @Param("pattern") String pattern, Pageable pageable);

    Optional<Customer> findByBusinessIdAndId(String businessId, long customerId);

    boolean existsByBusinessIdAndId(String businessId, long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.businessId = :businessId and c.id = :customerId")
    Optional<Customer> findLockedByBusinessIdAndId(@Param("businessId") String businessId,
            @Param("customerId") long customerId);

    @Query("select c.id from Customer c where c.businessId = :businessId order by c.createdAt desc, c.id desc")
    List<Long> findIdsByBusinessIdOrderByCreatedAtDescIdDesc(@Param("businessId") String businessId);
}
