package com.baakio.service.auth;

import com.baakio.dto.auth.EmailOtpVerifyRequest;
import com.baakio.enums.EmailOtpPurpose;
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
public class EmailAuthService {
    private final EmailOtpService codes;
    private final AccountIdentityRepository identities;
    private final AccountHolderRepository accountHolders;
    private final BusinessRepository businesses;
    private final AccountHolderService accountHolderService;

    public EmailAuthService(EmailOtpService codes, AccountIdentityRepository identities,
            AccountHolderRepository accountHolders, BusinessRepository businesses,
            AccountHolderService accountHolderService) {
        this.codes = codes;
        this.identities = identities;
        this.accountHolders = accountHolders;
        this.businesses = businesses;
        this.accountHolderService = accountHolderService;
    }

    @Transactional
    public AccountHolderPrincipal signIn(EmailOtpVerifyRequest request, EmailOtpPurpose purpose) {
        if (purpose == EmailOtpPurpose.LINK) throw invalidCode();
        if (!codes.consume(request.challengeId(), request.email(), purpose, request.otp())) throw invalidCode();
        String email = EmailOtpService.normalize(request.email());
        String existingId = identities.accountHolderId("EMAIL", email).orElse(null);
        if (purpose == EmailOtpPurpose.LOGIN) {
            if (existingId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_HOLDER_NOT_FOUND",
                    "No account uses this verified email.");
            return accountHolderService.principalById(existingId);
        }
        if (existingId != null || accountHolders.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_LINK_REQUIRED",
                    "This email already belongs to an account. Sign in and link it from settings.");
        }
        if (request.businessName() == null || request.businessName().isBlank()
                || request.displayName() == null || request.displayName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REGISTRATION_DETAILS_REQUIRED",
                    "Enter your name and shop name.");
        }
        String id = UUID.randomUUID().toString();
        String locale = request.preferredLocale() == null ? "en-IN" : request.preferredLocale();
        try {
            accountHolders.saveAndFlush(new AccountHolder(id, null, email, request.displayName().trim(), locale));
            Business business = new Business(UUID.randomUUID().toString(), request.businessName().trim(), id);
            business.setDefaultLocale(locale);
            businesses.saveAndFlush(business);
            identities.add("EMAIL", email, id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_LINK_REQUIRED", "This email already belongs to an account.");
        }
        return accountHolderService.principalById(id);
    }

    @Transactional
    public void linkEmail(AccountHolderPrincipal principal, EmailOtpVerifyRequest request) {
        if (!codes.consume(request.challengeId(), request.email(), EmailOtpPurpose.LINK, request.otp())) throw invalidCode();
        String email = EmailOtpService.normalize(request.email());
        String linkedId = identities.accountHolderId("EMAIL", email).orElse(null);
        if (linkedId != null) {
            if (linkedId.equals(principal.id())) return;
            throw new ApiException(HttpStatus.CONFLICT, "IDENTITY_ALREADY_LINKED", "This email is linked elsewhere.");
        }
        if (accountHolders.existsByEmailIgnoreCase(email)
                && !email.equalsIgnoreCase(principal.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDENTITY_ALREADY_LINKED", "This email is linked elsewhere.");
        }
        try {
            accountHolders.updateEmail(principal.id(), email);
            identities.add("EMAIL", email, principal.id());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "IDENTITY_ALREADY_LINKED", "This email is linked elsewhere.");
        }
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_OR_EXPIRED_OTP",
                "The code is invalid or expired. Request another code.");
    }
}
