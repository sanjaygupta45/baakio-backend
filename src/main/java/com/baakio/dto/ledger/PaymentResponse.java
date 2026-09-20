package com.baakio.dto.ledger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PaymentResponse(
        long id,
        long customerId,
        String amount,
        String method,
        LocalDate occurredOn,
        String reference,
        String outstanding,
        List<AllocationResponse> allocations,
        Instant postedAt
) {}
