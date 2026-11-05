package com.baakio.service.auth;

public record ShopCreatedEvent(
        String accountHolderId,
        String email,
        String displayName,
        String businessName,
        String locale
) {}
