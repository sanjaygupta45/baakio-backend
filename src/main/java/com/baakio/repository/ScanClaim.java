package com.baakio.repository;

public record ScanClaim(ScanJobRecord job, String token, int attemptNo) {}
