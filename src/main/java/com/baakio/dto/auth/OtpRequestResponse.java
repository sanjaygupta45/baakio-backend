package com.baakio.dto.auth;

public record OtpRequestResponse(
        String challengeId,
        int expiresInSeconds,
        int resendAfterSeconds,
        String developmentCode
) {}
