package com.baakio.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class FixedDevelopmentOtpCodeGenerator implements OtpCodeGenerator {
    private final String code;

    public FixedDevelopmentOtpCodeGenerator(@Value("${baakio.auth.otp.development-code}") String code) {
        if (!code.matches("^[0-9]{6}$")) throw new IllegalArgumentException("Development OTP must be six digits.");
        this.code = code;
    }

    @Override public String generate() { return code; }
}
