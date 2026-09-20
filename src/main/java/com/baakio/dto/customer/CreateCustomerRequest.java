package com.baakio.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 160) String displayName,
        @Pattern(regexp = "^$|^\\+91[6-9][0-9]{9}$") @Size(max = 13) String phoneE164,
        @Size(max = 1000) String notes,
        @Pattern(regexp = "en-IN|hi-IN") String preferredMessageLocale
) {}
