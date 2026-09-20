package com.baakio.repository;

import com.baakio.model.AccountHolder;
import com.baakio.security.AccountHolderPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, String> {

    boolean existsByPhoneE164(String phoneE164);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select new com.baakio.security.AccountHolderPrincipal(
                a.id, b.id, b.name, a.displayName, a.phoneE164, a.email, a.active)
              from AccountHolder a, Business b
             where b.accountHolderId = a.id
               and a.phoneE164 = :phone and a.active = true
             order by b.id
            """)
    List<AccountHolderPrincipal> findActiveByPhone(@Param("phone") String phoneE164, Pageable pageable);

    @Query("select a.preferredLocale from AccountHolder a where a.id = :accountHolderId")
    String findPreferredLocaleById(@Param("accountHolderId") String accountHolderId);

    @Modifying
    @Transactional
    @Query("update AccountHolder a set a.preferredLocale = :locale where a.id = :accountHolderId")
    int updatePreferredLocale(@Param("accountHolderId") String accountHolderId, @Param("locale") String locale);
}
