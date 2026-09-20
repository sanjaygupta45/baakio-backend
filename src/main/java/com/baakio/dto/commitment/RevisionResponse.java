package com.baakio.dto.commitment;

import java.time.Instant;
import java.time.LocalDate;

public record RevisionResponse(
        LocalDate oldDate,
        LocalDate newDate,
        String reason,
        Instant createdAt
) {}
