package com.baakio;

import com.baakio.model.AccountHolder;
import com.baakio.model.Business;
import com.baakio.model.Customer;
import com.baakio.repository.AccountHolderRepository;
import com.baakio.security.AccountHolderPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BaakioApplicationTests {
	@Autowired
	private AccountHolderRepository accountHolders;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@Transactional
	void resolvesAccountHolderBusinessAndGeneratesCustomerIdAfterFlush() {
		String accountHolderId = UUID.randomUUID().toString();
		String businessId = UUID.randomUUID().toString();
		AccountHolder accountHolder = new AccountHolder(accountHolderId, "+919876543210", null,
				"Account Holder", "en-IN");
		entityManager.persist(accountHolder);
		entityManager.persist(new Business(businessId, "ID generation check", accountHolderId));

		Customer customer = new Customer(businessId, "ID generation check", null, null, "en-IN");
		entityManager.persist(customer);
		entityManager.flush();

		assertNotNull(customer.getId());
		assertTrue(customer.getId() > 0);
		AccountHolderPrincipal principal = accountHolders.findActiveByPhone(
				"+919876543210", PageRequest.of(0, 1)).getFirst();
		assertEquals(accountHolderId, principal.id());
		assertEquals(businessId, principal.businessId());
	}
}
