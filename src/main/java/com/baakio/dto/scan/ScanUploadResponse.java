package com.baakio.dto.scan;

import java.time.Instant;

public record ScanUploadResponse(long scanId, String status, Instant createdAt) {}
