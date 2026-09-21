package com.baakio.dto.scan;

import com.baakio.ai.ScanDraft;

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
