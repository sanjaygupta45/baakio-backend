package com.baakio.dto.customer;

import java.time.Instant;

public record CustomerResponse(
        long id,
        String displayName,
        String phoneE164,
        String notes,
        String preferredMessageLocale,
        String outstanding,
        String totalPurchases,
        String totalPaid,
        CommitmentSummary nextCommitment,
        boolean openingBalanceSet,
        Instant createdAt
) {}
