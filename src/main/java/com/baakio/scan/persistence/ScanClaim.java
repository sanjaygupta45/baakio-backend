package com.baakio.scan.persistence;

public record ScanClaim(ScanJobRecord job, String token, int attemptNo) {}
