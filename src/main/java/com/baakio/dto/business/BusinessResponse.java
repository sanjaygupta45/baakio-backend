package com.baakio.dto.business;

public record BusinessResponse(
        String id,
        String name,
        String timezone,
        String currency,
        String upiVpa,
        String upiLinkedMobileE164,
        String upiPayeeName,
        boolean paymentDetailsAccountHolderConfirmed
) {}
