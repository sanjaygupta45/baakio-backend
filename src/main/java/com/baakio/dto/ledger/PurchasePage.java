package com.baakio.dto.ledger;

import java.util.List;

public record PurchasePage(
        List<PurchaseResponse> items,
        int page,
        int pageSize,
        long total
) {}
