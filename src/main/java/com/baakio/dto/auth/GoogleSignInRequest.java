package com.baakio.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoogleSignInRequest(
        @NotBlank @Size(max = 8192) String idToken,
        @Size(max = 160) String displayName,
        @Size(max = 160) String businessName,
        @Pattern(regexp = "^(en-IN|hi-IN)$") String preferredLocale
) {}
