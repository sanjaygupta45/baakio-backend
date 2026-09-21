package com.baakio.scan.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ScanExtraction(
        @JsonProperty("is_new_purchase") Boolean isNewPurchase,
        @JsonProperty("occurred_on") String occurredOn,
        String description,
        String discount,
        @JsonProperty("written_total") String writtenTotal,
        List<ScanExtractionLine> items
) {}
