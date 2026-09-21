package com.baakio.scan.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ScanLineEdit(
        @NotBlank @Size(max = 200) String name,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @Size(max = 40) String unit,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice
) {}
