package com.baakio.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LocaleRequest(
        @NotBlank @Pattern(regexp = "en-IN|hi-IN") String preferredLocale
) {}
