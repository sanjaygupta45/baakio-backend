package com.baakio.service.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

@Component
@Profile("!dev")
public class SecureOtpCodeGenerator implements OtpCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }
}
