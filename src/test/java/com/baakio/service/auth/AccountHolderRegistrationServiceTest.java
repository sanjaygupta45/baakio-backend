package com.baakio.service.auth;

import com.baakio.dto.auth.RegisterRequest;
import com.baakio.model.AccountHolder;
import com.baakio.model.Business;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.repository.BusinessRepository;
import com.baakio.repository.AccountHolderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountHolderRegistrationServiceTest {

    @Test
    void savesAccountHolderAndBusinessAndBuildsPrincipalWithoutReloadingThem() {
        FakeRepositories repositories = new FakeRepositories();
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        AccountHolderRegistrationService registrations = new AccountHolderRegistrationService(
                repositories.accountHolders, repositories.businesses, events);

        AccountHolderPrincipal principal = registrations.register(new RegisterRequest(
                "+919876543210", "challenge-id", "123456", " Account Holder ", " My Shop ",
                " Holder@Example.com ", "hi-IN"));

        Business business = repositories.business;
        AccountHolder accountHolder = repositories.accountHolder;

        assertEquals(accountHolder.getId(), principal.id());
        assertEquals(business.getId(), principal.businessId());
        assertEquals("My Shop", principal.businessName());
        assertEquals("Account Holder", principal.displayName());
        assertEquals("+919876543210", principal.phoneNumber());
        assertEquals("holder@example.com", principal.email());
        assertEquals(accountHolder.getId(), business.getAccountHolderId());
        verify(events).publishEvent(new ShopCreatedEvent(accountHolder.getId(), "holder@example.com",
                "Account Holder", "My Shop", "hi-IN"));
    }

    private static final class FakeRepositories {
        private Business business;
        private AccountHolder accountHolder;

        private final AccountHolderRepository accountHolders = proxy(AccountHolderRepository.class, (instance, method, arguments) -> switch (method.getName()) {
            case "existsByPhoneE164" -> false;
            case "existsByEmailIgnoreCase" -> {
                assertEquals("holder@example.com", arguments[0]);
                yield false;
            }
            case "save", "saveAndFlush" -> {
                accountHolder = (AccountHolder) arguments[0];
                yield accountHolder;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });

        private final BusinessRepository businesses = proxy(BusinessRepository.class, (instance, method, arguments) -> {
            if (method.getName().equals("save") || method.getName().equals("saveAndFlush")) {
                business = (Business) arguments[0];
                return business;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> repositoryType, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(repositoryType.getClassLoader(),
                    new Class<?>[]{repositoryType}, handler);
        }
    }
}
