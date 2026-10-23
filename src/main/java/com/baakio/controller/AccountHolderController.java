package com.baakio.controller;

import com.baakio.dto.auth.LocaleRequest;
import com.baakio.dto.auth.LoginRequest;
import com.baakio.dto.auth.LoginResponse;
import com.baakio.dto.auth.AccountHolderResponse;
import com.baakio.dto.auth.OtpRequest;
import com.baakio.dto.auth.OtpRequestResponse;
import com.baakio.dto.auth.RegisterRequest;
import com.baakio.dto.auth.EmailOtpRequest;
import com.baakio.dto.auth.EmailOtpVerifyRequest;
import com.baakio.dto.auth.GoogleSignInRequest;
import com.baakio.enums.EmailOtpPurpose;
import com.baakio.service.auth.EmailAuthService;
import com.baakio.service.auth.EmailOtpService;
import com.baakio.service.auth.FederatedAuthService;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.service.AccountHolderService;
import com.baakio.service.auth.AuthSessionService;
import com.baakio.service.auth.PasswordlessAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import com.baakio.exception.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AccountHolderController {
    private final PasswordlessAuthService auth;
    private final AuthSessionService sessions;
    private final AccountHolderService accountHolders;
    private final FederatedAuthService federatedAuth;
    private final EmailAuthService emailAuth;
    private final EmailOtpService emailCodes;
    private final String googleClientId;
    private final boolean emailEnabled;
    private final boolean legacyPhoneEnabled;

    public AccountHolderController(PasswordlessAuthService auth, AuthSessionService sessions,
                                   AccountHolderService accountHolders,
                                   FederatedAuthService federatedAuth, EmailAuthService emailAuth,
                                   EmailOtpService emailCodes,
                                   @Value("${baakio.auth.google.client-id}") String googleClientId,
                                   @Value("${baakio.auth.email.enabled}") boolean emailEnabled,
                                   @Value("${baakio.auth.legacy-phone-enabled}") boolean legacyPhoneEnabled) {
        this.auth = auth;
        this.sessions = sessions;
        this.accountHolders = accountHolders;
        this.federatedAuth = federatedAuth;
        this.emailAuth = emailAuth;
        this.emailCodes = emailCodes;
        this.googleClientId = googleClientId;
        this.emailEnabled = emailEnabled;
        this.legacyPhoneEnabled = legacyPhoneEnabled;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

    @GetMapping("/auth/config")
    public Map<String, Object> authConfig() {
        return Map.of("googleClientId", googleClientId, "emailEnabled", emailEnabled,
                "legacyPhoneEnabled", legacyPhoneEnabled);
    }

    @PostMapping("/auth/google")
    public LoginResponse google(@Valid @RequestBody GoogleSignInRequest body,
                                HttpServletRequest request, HttpServletResponse response) {
        AccountHolderPrincipal principal = federatedAuth.signIn(body);
        sessions.signIn(principal, request, response);
        return new LoginResponse(accountHolders.accountHolder(principal));
    }

    @PostMapping("/auth/email/request")
    public com.baakio.dto.auth.OtpRequestResponse requestEmail(@Valid @RequestBody EmailOtpRequest body) {
        if (body.purpose() == EmailOtpPurpose.LINK) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_AUTH_PURPOSE", "Use the account linking endpoint.");
        return emailCodes.request(body.email(), body.purpose(), body.preferredLocale());
    }

    @PostMapping("/auth/email/login")
    public LoginResponse emailLogin(@Valid @RequestBody EmailOtpVerifyRequest body,
                                    HttpServletRequest request, HttpServletResponse response) {
        AccountHolderPrincipal principal = emailAuth.signIn(body, EmailOtpPurpose.LOGIN);
        sessions.signIn(principal, request, response);
        return new LoginResponse(accountHolders.accountHolder(principal));
    }

    @PostMapping("/auth/email/register")
    public LoginResponse emailRegister(@Valid @RequestBody EmailOtpVerifyRequest body,
                                       HttpServletRequest request, HttpServletResponse response) {
        AccountHolderPrincipal principal = emailAuth.signIn(body, EmailOtpPurpose.REGISTER);
        sessions.signIn(principal, request, response);
        return new LoginResponse(accountHolders.accountHolder(principal));
    }

    @PostMapping("/account-holder/identities/google")
    public Map<String, String> linkGoogle(@AuthenticationPrincipal AccountHolderPrincipal principal,
                                          @Valid @RequestBody GoogleSignInRequest body) {
        federatedAuth.linkGoogle(principal, body.idToken());
        return Map.of("status", "linked");
    }

    @PostMapping("/account-holder/identities/email/request")
    public OtpRequestResponse requestEmailLink(@AuthenticationPrincipal AccountHolderPrincipal principal,
                                               @Valid @RequestBody EmailOtpRequest body) {
        return emailCodes.request(body.email(), EmailOtpPurpose.LINK, body.preferredLocale());
    }

    @PostMapping("/account-holder/identities/email/verify")
    public Map<String, String> linkEmail(@AuthenticationPrincipal AccountHolderPrincipal principal,
                                         @Valid @RequestBody EmailOtpVerifyRequest body) {
        emailAuth.linkEmail(principal, body);
        return Map.of("status", "linked");
    }

    @PostMapping("/auth/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest body) {
        return auth.request(body);
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest body,
                               HttpServletRequest request, HttpServletResponse response) {
        AccountHolderPrincipal principal = auth.login(body);
        sessions.signIn(principal, request, response);
        return new LoginResponse(accountHolders.accountHolder(principal));
    }

    @PostMapping("/auth/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest body,
                                  HttpServletRequest request, HttpServletResponse response) {
        AccountHolderPrincipal principal = auth.register(body);
        sessions.signIn(principal, request, response);
        return new LoginResponse(accountHolders.accountHolder(principal));
    }

    @PostMapping("/auth/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        sessions.signOut(request);
        return Map.of("status", "signed_out");
    }

    @GetMapping("/account-holder")
    public AccountHolderResponse accountHolder(@AuthenticationPrincipal AccountHolderPrincipal principal) {
        return accountHolders.accountHolder(principal);
    }

    @PatchMapping("/account-holder/preferences")
    public Map<String, String> updatePreferences(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @Valid @RequestBody LocaleRequest body) {
        accountHolders.updatePreferredLocale(principal.id(), body.preferredLocale());
        return Map.of("preferredLocale", body.preferredLocale());
    }
}
