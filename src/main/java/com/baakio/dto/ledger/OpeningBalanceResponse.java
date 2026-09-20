package com.baakio.dto.ledger;

import java.time.Instant;
import java.time.LocalDate;

public record OpeningBalanceResponse(
        long customerId,
        String amount,
        LocalDate cutoffDate,
        String outstanding,
        Instant postedAt
) {}
