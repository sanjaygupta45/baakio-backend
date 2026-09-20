package com.baakio.dto.ledger;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaidNowRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String method,
        @NotNull Boolean confirmedByAccountHolder
) {}
