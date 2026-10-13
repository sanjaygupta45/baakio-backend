package com.baakio.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailOtpVerifyRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Size(max = 36) String challengeId,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp,
        @Size(max = 160) String displayName,
        @Size(max = 160) String businessName,
        @Pattern(regexp = "^(en-IN|hi-IN)$") String preferredLocale
) {}
