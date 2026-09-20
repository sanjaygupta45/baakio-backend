package com.baakio.dto.auth;

public record AccountHolderResponse(
        String id,
        String phoneNumber,
        String email,
        String displayName,
        String businessId,
        String businessName,
        String preferredLocale
) {}
