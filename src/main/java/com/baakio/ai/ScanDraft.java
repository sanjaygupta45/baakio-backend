package com.baakio.ai;

import java.time.LocalDate;
import java.util.List;

public record ScanDraft(
        LocalDate occurredOn,
        String description,
        String discount,
        String writtenTotal,
        String calculatedTotal,
        List<ScanDraftLine> lines,
        List<String> flags
) {}
