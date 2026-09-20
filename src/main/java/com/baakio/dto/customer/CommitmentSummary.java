package com.baakio.dto.customer;

import java.time.LocalDate;
import java.math.BigDecimal;
import com.baakio.utils.MoneyAmounts;

public record CommitmentSummary(
        long id,
        String remaining,
        LocalDate dueDate,
        String status
) {
    public CommitmentSummary(long id, BigDecimal remaining, LocalDate dueDate, String status) {
        this(id, MoneyAmounts.text(remaining), dueDate, status);
    }
}
