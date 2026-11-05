package com.baakio.service.auth;

import com.baakio.dto.auth.RegisterRequest;
import com.baakio.model.AccountHolder;
import com.baakio.model.Business;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.repository.BusinessRepository;
import com.baakio.repository.AccountHolderRepository;
import com.baakio.exception.ApiException;
import com.baakio.utils.IndianPhoneNumbers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AccountHolderRegistrationService {
    private final AccountHolderRepository accountHolders;
    private final BusinessRepository businesses;
    private final ApplicationEventPublisher events;

    public AccountHolderRegistrationService(AccountHolderRepository accountHolders,
                                            BusinessRepository businesses,
                                            ApplicationEventPublisher events) {
        this.accountHolders = accountHolders;
        this.businesses = businesses;
        this.events = events;
    }

    @Transactional
    public AccountHolderPrincipal register(RegisterRequest request) {
        String phone = IndianPhoneNumbers.normalize(request.phoneNumber());
        if (accountHolders.existsByPhoneE164(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_HOLDER_PHONE_ALREADY_REGISTERED",
                    "This phone number already has a Baakio account. Sign in instead.");
        }
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        if (email != null && accountHolders.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_HOLDER_EMAIL_ALREADY_REGISTERED",
                    "This email address is already linked to a Baakio account.");
        }
        String locale = request.preferredLocale() == null ? "en-IN" : request.preferredLocale();
        String accountHolderId = UUID.randomUUID().toString();
        String businessId = UUID.randomUUID().toString();
        String displayName = request.displayName().trim();
        String businessName = request.businessName().trim();
        try {
            accountHolders.saveAndFlush(new AccountHolder(accountHolderId, phone, email, displayName, locale));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_HOLDER_ALREADY_EXISTS",
                    "An account holder already uses this phone number or email address.");
        }
        Business business = new Business(businessId, businessName, accountHolderId);
        business.setDefaultLocale(locale);
        // Flush here so business persistence errors surface inside this method
        // and the surrounding transaction rolls back before a response is built.
        businesses.saveAndFlush(business);
        if (email != null) {
            events.publishEvent(new ShopCreatedEvent(accountHolderId, email, displayName, businessName, locale));
        }
        return new AccountHolderPrincipal(accountHolderId, businessId, businessName,
                displayName, phone, email, true);
    }
}
