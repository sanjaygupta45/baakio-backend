package com.baakio.service.auth;

import com.baakio.dto.auth.LoginRequest;
import com.baakio.dto.auth.OtpRequest;
import com.baakio.dto.auth.OtpRequestResponse;
import com.baakio.dto.auth.RegisterRequest;
import com.baakio.enums.OtpPurpose;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.service.AccountHolderService;
import com.baakio.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PasswordlessAuthService {
    private final OtpService otps;
    private final AccountHolderService accountHolders;
    private final AccountHolderRegistrationService registrations;

    public PasswordlessAuthService(OtpService otps, AccountHolderService accountHolders,
                                   AccountHolderRegistrationService registrations) {
        this.otps = otps;
        this.accountHolders = accountHolders;
        this.registrations = registrations;
    }

    public OtpRequestResponse request(OtpRequest request) {
        return otps.request(request.phoneNumber(), request.purpose(), request.preferredLocale());
    }

    public AccountHolderPrincipal login(LoginRequest request) {
        if (!otps.consume(request.challengeId(), request.phoneNumber(), OtpPurpose.LOGIN, request.otp())) {
            throw invalidCode();
        }
        return accountHolders.principalByPhone(request.phoneNumber());
    }

    public AccountHolderPrincipal register(RegisterRequest request) {
        if (!otps.consume(request.challengeId(), request.phoneNumber(), OtpPurpose.REGISTER, request.otp())) {
            throw invalidCode();
        }
        return registrations.register(request);
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_OR_EXPIRED_OTP",
                "The code is invalid or expired. Request a new code and try again.");
    }
}
