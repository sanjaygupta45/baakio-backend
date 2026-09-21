package com.baakio.scan.api;

import java.time.Instant;

public record ScanUploadResponse(long scanId, String status, Instant createdAt) {}
