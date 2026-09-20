package com.baakio.controller;

import com.baakio.dto.auth.LocaleRequest;
import com.baakio.dto.auth.LoginRequest;
import com.baakio.dto.auth.LoginResponse;
import com.baakio.dto.auth.AccountHolderResponse;
import com.baakio.dto.auth.OtpRequest;
import com.baakio.dto.auth.OtpRequestResponse;
import com.baakio.dto.auth.RegisterRequest;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.service.AccountHolderService;
import com.baakio.service.auth.AuthSessionService;
import com.baakio.service.auth.PasswordlessAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

    public AccountHolderController(PasswordlessAuthService auth, AuthSessionService sessions,
                                   AccountHolderService accountHolders) {
        this.auth = auth;
        this.sessions = sessions;
        this.accountHolders = accountHolders;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

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
