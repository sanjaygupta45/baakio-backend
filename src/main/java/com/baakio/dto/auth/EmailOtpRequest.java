package com.baakio.dto.auth;

import com.baakio.enums.EmailOtpPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailOtpRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotNull EmailOtpPurpose purpose,
        @Pattern(regexp = "^(en-IN|hi-IN)$") String preferredLocale
) {}
