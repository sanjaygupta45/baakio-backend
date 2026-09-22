package com.baakio.dto.scan;

public record ScanDraftRecord(
        long id,
        long scanJobId,
        String extractedJson,
        String editedJson,
        String writtenTotal,
        String calculatedTotal,
        String flagsJson,
        long version,
        Long postedPurchaseId
) {}
