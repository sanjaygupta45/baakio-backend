package com.baakio.dto.commitment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCommitmentRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDate dueDate,
        @Size(max = 500) String note
) {}
