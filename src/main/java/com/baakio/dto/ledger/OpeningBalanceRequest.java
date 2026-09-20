package com.baakio.dto.ledger;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OpeningBalanceRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDate cutoffDate,
        @NotNull Boolean acknowledged
) {}
