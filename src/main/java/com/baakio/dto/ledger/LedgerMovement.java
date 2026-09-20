package com.baakio.dto.ledger;

import java.time.Instant;
import java.time.LocalDate;

public record LedgerMovement(
        String type,
        long id,
        LocalDate occurredOn,
        String description,
        String amount,
        String direction,
        String method,
        Instant postedAt
) {}
