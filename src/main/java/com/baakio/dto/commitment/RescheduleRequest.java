package com.baakio.dto.commitment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RescheduleRequest(
        @NotNull LocalDate newDueDate,
        @Size(max = 500) String reason
) {}
