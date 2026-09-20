package com.baakio.dto.ledger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreatePaymentRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        LocalDate occurredOn,
        @NotBlank String method,
        @Size(max = 160) String reference,
        @NotNull Boolean confirmedByAccountHolder,
        List<@Valid PaymentAllocationRequest> allocations
) {}
