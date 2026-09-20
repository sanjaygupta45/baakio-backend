package com.baakio.dto.auth;

import com.baakio.enums.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpRequest(
        @NotBlank @Size(max = 13) @Pattern(regexp = "^(?:\\+91|91|0)?[6-9][0-9]{9}$") String phoneNumber,
        @NotNull OtpPurpose purpose,
        @Pattern(regexp = "^(en-IN|hi-IN)$") String preferredLocale
) {}
