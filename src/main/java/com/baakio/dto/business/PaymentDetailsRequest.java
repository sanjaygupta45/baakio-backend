package com.baakio.dto.business;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentDetailsRequest(
        String upiVpa,
        @Pattern(regexp = "^$|^\\+91[6-9][0-9]{9}$") @Size(max = 13) String upiLinkedMobileE164,
        String upiPayeeName,
        @NotNull Boolean accountHolderConfirmed
) {}
