package com.baakio.dto.ledger;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PurchaseLineRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        @Size(max = 40) String unit,
        @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice
) {}
