package com.baakio.scan.api;

import com.baakio.scan.persistence.ScanDraft;

import java.time.Instant;

public record ScanResponse(
        long scanId,
        long customerId,
        String status,
        int attemptCount,
        String errorCode,
        boolean imageAvailable,
        Instant createdAt,
        Long draftVersion,
        ScanDraft draft
) {}
