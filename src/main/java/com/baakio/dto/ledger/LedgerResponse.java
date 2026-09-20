package com.baakio.dto.ledger;

import java.time.LocalDate;
import java.util.List;

public record LedgerResponse(
        long customerId,
        LocalDate from,
        LocalDate to,
        String openingAtFrom,
        String purchasesInPeriod,
        String paymentsInPeriod,
        String closingAtTo,
        String lifetimeOutstanding,
        List<LedgerMovement> movements
) {}
