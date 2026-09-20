package com.baakio.dto.ledger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseRequest(
        @NotBlank @Pattern(regexp = "QUICK|ITEMIZED") String mode,
        LocalDate occurredOn,
        @Size(max = 500) String description,
        @DecimalMin(value = "0.00") BigDecimal amount,
        @DecimalMin(value = "0.00") BigDecimal discount,
        List<@Valid PurchaseLineRequest> lines,
        @Valid PaidNowRequest paidNow
) {}
