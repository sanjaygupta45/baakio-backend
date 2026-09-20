package com.baakio.dto.reminder;

import java.time.Instant;

public record ReminderResponse(
        String locale,
        String format,
        Instant generatedAt,
        String recipient,
        String periodStart,
        String periodEnd,
        String openingBalance,
        String purchases,
        String payments,
        String closingBalance,
        String outstandingAsOfNow,
        String message,
        String whatsappUrl,
        boolean sent
) {}
