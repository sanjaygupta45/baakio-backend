package com.baakio.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 13) @Pattern(regexp = "^(?:\\+91|91|0)?[6-9][0-9]{9}$") String phoneNumber,
        @NotBlank @Size(max = 36) String challengeId,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp
) {}
