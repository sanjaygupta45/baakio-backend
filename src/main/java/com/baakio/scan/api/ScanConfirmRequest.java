package com.baakio.scan.api;

import com.baakio.dto.ledger.PaidNowRequest;
import com.baakio.dto.ledger.PurchaseLineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ScanConfirmRequest(
        @NotNull Long version,
        @NotNull LocalDate occurredOn,
        @Size(max = 500) String description,
        @DecimalMin(value = "0.00") BigDecimal discount,
        @NotEmpty List<@Valid PurchaseLineRequest> lines,
        @Valid PaidNowRequest paidNow
) {}
