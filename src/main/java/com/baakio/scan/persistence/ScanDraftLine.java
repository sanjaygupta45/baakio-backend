package com.baakio.scan.persistence;

import java.util.List;

public record ScanDraftLine(
        String name,
        String quantity,
        String unit,
        String unitPrice,
        String lineTotal,
        List<String> flags
) {}
