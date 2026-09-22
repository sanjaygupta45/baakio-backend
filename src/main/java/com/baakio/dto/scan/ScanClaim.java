package com.baakio.dto.scan;

public record ScanClaim(ScanJobRecord job, String token, int attemptNo) {}
