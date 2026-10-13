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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FederatedAuthServiceTest {
    private final GoogleIdentityVerifier verifier = mock(GoogleIdentityVerifier.class);
    private final AccountIdentityRepository identities = mock(AccountIdentityRepository.class);
    private final AccountHolderRepository holders = mock(AccountHolderRepository.class);
    private final BusinessRepository businesses = mock(BusinessRepository.class);
    private final AccountHolderService holderService = mock(AccountHolderService.class);
    private FederatedAuthService service;

    @BeforeEach
    void setUp() {
        service = new FederatedAuthService(verifier, identities, holders, businesses, holderService);
        when(verifier.verify("valid-id-token")).thenReturn(
                new GoogleIdentityVerifier.VerifiedGoogleIdentity("google-sub-123", "owner@example.com", "Owner"));
    }

    @Test
    void existingGoogleSubjectSignsIntoItsOwnAccount() {
        AccountHolderPrincipal principal = new AccountHolderPrincipal("holder-1", "shop-1", "Shop", "Owner",
                null, "owner@example.com", true);
        when(identities.accountHolderId("GOOGLE", "google-sub-123")).thenReturn(Optional.of("holder-1"));
        when(holderService.principalById("holder-1")).thenReturn(principal);

        assertSame(principal, service.signIn(new GoogleSignInRequest("valid-id-token", null, null, null)));
        verifyNoInteractions(holders, businesses);
    }

    @Test
    void matchingEmailDoesNotSilentlyTakeOverLegacyAccount() {
        when(identities.accountHolderId("GOOGLE", "google-sub-123")).thenReturn(Optional.empty());
        when(holders.existsByEmailIgnoreCase("owner@example.com")).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.signIn(new GoogleSignInRequest("valid-id-token", "Owner", "Shop", "en-IN")));
        assertEquals("ACCOUNT_LINK_REQUIRED", error.code());
        verify(holders, never()).saveAndFlush(any());
    }

    @Test
    void newVerifiedGoogleIdentityCreatesOneShop() {
        when(identities.accountHolderId("GOOGLE", "google-sub-123")).thenReturn(Optional.empty());
        when(holders.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);
        when(holderService.principalById(anyString())).thenAnswer(invocation ->
                new AccountHolderPrincipal(invocation.getArgument(0), "shop-1", "Shop", "Owner",
                        null, "owner@example.com", true));

        AccountHolderPrincipal principal = service.signIn(
                new GoogleSignInRequest("valid-id-token", "Owner", "Shop", "en-IN"));
        ArgumentCaptor<AccountHolder> account = ArgumentCaptor.forClass(AccountHolder.class);
        ArgumentCaptor<Business> business = ArgumentCaptor.forClass(Business.class);
        verify(holders).saveAndFlush(account.capture());
        verify(businesses).saveAndFlush(business.capture());
        assertEquals("owner@example.com", account.getValue().getEmail());
        assertNull(account.getValue().getPhoneE164());
        assertEquals(account.getValue().getId(), business.getValue().getAccountHolderId());
        verify(identities).add("GOOGLE", "google-sub-123", principal.id());
    }
}
