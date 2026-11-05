package com.baakio.service.auth;

import com.baakio.dto.auth.EmailOtpVerifyRequest;
import com.baakio.enums.EmailOtpPurpose;
import com.baakio.exception.ApiException;
import com.baakio.repository.AccountHolderRepository;
import com.baakio.repository.AccountIdentityRepository;
import com.baakio.repository.BusinessRepository;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.service.AccountHolderService;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailAuthServiceTest {
    private final EmailOtpService codes = mock(EmailOtpService.class);
    private final AccountIdentityRepository identities = mock(AccountIdentityRepository.class);
    private final AccountHolderRepository holders = mock(AccountHolderRepository.class);
    private final BusinessRepository businesses = mock(BusinessRepository.class);
    private final AccountHolderService holderService = mock(AccountHolderService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private EmailAuthService service;
    private final EmailOtpVerifyRequest request = new EmailOtpVerifyRequest(
            "OWNER@EXAMPLE.COM", "challenge-1", "123456", "Owner", "Shop", "en-IN");

    @BeforeEach
    void setUp() {
        service = new EmailAuthService(codes, identities, holders, businesses, holderService, events);
    }

    @Test
    void codeMustBeValidBeforeLookupOrRegistration() {
        when(codes.consume("challenge-1", "OWNER@EXAMPLE.COM", EmailOtpPurpose.LOGIN, "123456"))
                .thenReturn(false);
        ApiException error = assertThrows(ApiException.class, () -> service.signIn(request, EmailOtpPurpose.LOGIN));
        assertEquals("INVALID_OR_EXPIRED_OTP", error.code());
        verifyNoInteractions(identities, holders, businesses);
    }

    @Test
    void verifiedEmailWithExistingUnlinkedAccountCannotClaimIt() {
        when(codes.consume("challenge-1", "OWNER@EXAMPLE.COM", EmailOtpPurpose.REGISTER, "123456"))
                .thenReturn(true);
        when(identities.accountHolderId("EMAIL", "owner@example.com")).thenReturn(Optional.empty());
        when(holders.existsByEmailIgnoreCase("owner@example.com")).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.signIn(request, EmailOtpPurpose.REGISTER));
        assertEquals("ACCOUNT_LINK_REQUIRED", error.code());
        verify(holders, never()).saveAndFlush(any());
    }

    @Test
    void authenticatedHolderCanLinkVerifiedEmail() {
        AccountHolderPrincipal principal = new AccountHolderPrincipal("holder-1", "shop-1", "Shop", "Owner",
                null, null, true);
        when(codes.consume("challenge-1", "OWNER@EXAMPLE.COM", EmailOtpPurpose.LINK, "123456"))
                .thenReturn(true);
        when(identities.accountHolderId("EMAIL", "owner@example.com")).thenReturn(Optional.empty());

        service.linkEmail(principal, request);

        verify(holders).updateEmail("holder-1", "owner@example.com");
        verify(identities).add("EMAIL", "owner@example.com", "holder-1");
    }
}
