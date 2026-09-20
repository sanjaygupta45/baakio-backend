package com.baakio.service;

import com.baakio.dto.auth.AccountHolderResponse;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.exception.ApiException;
import com.baakio.repository.AccountHolderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccountHolderService {
    private final AccountHolderRepository accountHolders;

    public AccountHolderService(AccountHolderRepository accountHolders) {
        this.accountHolders = accountHolders;
    }

    public AccountHolderResponse accountHolder(AccountHolderPrincipal principal) {
        return new AccountHolderResponse(principal.id(), principal.phoneNumber(), principal.email(), principal.displayName(),
                principal.businessId(), principal.businessName(), preferredLocale(principal.id()));
    }

    public AccountHolderPrincipal principalByPhone(String rawPhone) {
        String phone = com.baakio.utils.IndianPhoneNumbers.normalize(rawPhone);
        AccountHolderPrincipal principal = accountHolders.findActiveByPhone(phone, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_HOLDER_NOT_FOUND",
                    "No account holder is registered with this phone number. Create an account first.");
        }
        return principal;
    }

    public String preferredLocale(String accountHolderId) {
        String locale = accountHolders.findPreferredLocaleById(accountHolderId);
        if (locale == null) throw ApiException.notFound();
        return locale;
    }

    public void updatePreferredLocale(String accountHolderId, String locale) {
        accountHolders.updatePreferredLocale(accountHolderId, locale);
    }
}
