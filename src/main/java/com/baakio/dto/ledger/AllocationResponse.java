package com.baakio.dto.ledger;

import java.math.BigDecimal;
import com.baakio.utils.MoneyAmounts;

public record AllocationResponse(
        long commitmentId,
        String amount,
        String remaining
) {
    public AllocationResponse(long commitmentId, BigDecimal amount, BigDecimal remaining) {
        this(commitmentId, MoneyAmounts.text(amount), MoneyAmounts.text(remaining));
    }
}
