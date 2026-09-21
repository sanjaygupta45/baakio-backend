package com.baakio.repository;

import java.time.Instant;

public record ScanJobRecord(
        long id,
        String businessId,
        long customerId,
        String objectKey,
        String sha256,
        String mimeType,
        String status,
        int attemptCount,
        String errorCode,
        String uploadKey,
        boolean keepAttachment,
        Instant createdAt,
        Instant lockedAt,
        String claimToken,
        Instant imageDeletedAt
) {}
