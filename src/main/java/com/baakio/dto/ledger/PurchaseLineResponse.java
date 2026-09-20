package com.baakio.dto.ledger;

public record PurchaseLineResponse(
        String name,
        String quantity,
        String unit,
        String unitPrice,
        String lineTotal
) {}
