package com.baakio.dto.ledger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseResponse(
        long id,
        long customerId,
        String mode,
        LocalDate occurredOn,
        String description,
        String subtotal,
        String discount,
        String total,
        String paidNow,
        String outstanding,
        List<PurchaseLineResponse> lines,
        Instant postedAt
) {}
