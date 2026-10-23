package com.baakio.service.auth;

import com.baakio.dto.auth.GoogleSignInRequest;
import com.baakio.exception.ApiException;
import com.baakio.model.AccountHolder;
import com.baakio.model.Business;
import com.baakio.repository.AccountHolderRepository;
import com.baakio.repository.AccountIdentityRepository;
import com.baakio.repository.BusinessRepository;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.service.AccountHolderService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FederatedAuthService {
    private final GoogleIdentityVerifier verifier;
    private final AccountIdentityRepository identities;
    private final AccountHolderRepository accountHolders;
    private final BusinessRepository businesses;
    private final AccountHolderService accountHolderService;

    public FederatedAuthService(GoogleIdentityVerifier verifier, AccountIdentityRepository identities,
            AccountHolderRepository accountHolders, BusinessRepository businesses,
            AccountHolderService accountHolderService) {
        this.verifier = verifier;
        this.identities = identities;
        this.accountHolders = accountHolders;
        this.businesses = businesses;
        this.accountHolderService = accountHolderService;
    }

    @Transactional
    public AccountHolderPrincipal signIn(GoogleSignInRequest request) {
        GoogleIdentityVerifier.VerifiedGoogleIdentity google = verifier.verify(request.idToken());
        String existingId = identities.accountHolderId("GOOGLE", google.subject()).orElse(null);
        if (existingId != null) return accountHolderService.principalById(existingId);
        if (accountHolders.existsByEmailIgnoreCase(google.email())) throw linkRequired();
        if (request.businessName() == null || request.businessName().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "REGISTRATION_DETAILS_REQUIRED",
                    "Enter a shop name to create your account.");
        }
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? google.displayName() : request.displayName().trim();
        if (displayName == null || displayName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DISPLAY_NAME_REQUIRED", "Enter your name.");
        }
        String locale = request.preferredLocale() == null ? "en-IN" : request.preferredLocale();
        String id = UUID.randomUUID().toString();
        try {
            accountHolders.saveAndFlush(new AccountHolder(id, null, google.email(), displayName, locale));
            Business business = new Business(UUID.randomUUID().toString(), request.businessName().trim(), id);
            business.setDefaultLocale(locale);
            businesses.saveAndFlush(business);
            identities.add("GOOGLE", google.subject(), id);
        } catch (DataIntegrityViolationException exception) {
            throw linkRequired();
        }
        return accountHolderService.principalById(id);
    }

    @Transactional
    public void linkGoogle(AccountHolderPrincipal principal, String idToken) {
        GoogleIdentityVerifier.VerifiedGoogleIdentity google = verifier.verify(idToken);
        String linkedId = identities.accountHolderId("GOOGLE", google.subject()).orElse(null);
        if (linkedId != null) {
            if (linkedId.equals(principal.id())) return;
            throw new ApiException(HttpStatus.CONFLICT, "IDENTITY_ALREADY_LINKED", "This Google account is linked elsewhere.");
        }
        if (accountHolders.existsByEmailIgnoreCase(google.email())
                && !google.email().equalsIgnoreCase(principal.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDENTITY_ALREADY_LINKED",
                    "This Google email belongs to another account.");
        }
        try {
            identities.add("GOOGLE", google.subject(), principal.id());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "IDENTITY_ALREADY_LINKED", "This Google account is linked elsewhere.");
        }
    }

    private ApiException linkRequired() {
        return new ApiException(HttpStatus.CONFLICT, "ACCOUNT_LINK_REQUIRED",
                "An account already uses this email. Sign in using its existing method and link Google from settings.");
    }
}
