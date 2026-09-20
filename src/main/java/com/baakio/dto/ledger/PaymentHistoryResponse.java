package com.baakio.dto.ledger;

import java.time.Instant;
import java.time.LocalDate;

public record PaymentHistoryResponse(
        long id,
        String amount,
        String method,
        LocalDate occurredOn,
        String reference,
        Instant postedAt
) {}
