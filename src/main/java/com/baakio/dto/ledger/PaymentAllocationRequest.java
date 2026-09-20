package com.baakio.dto.ledger;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentAllocationRequest(
        @NotNull Long commitmentId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {}
