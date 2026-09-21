package com.baakio.scan.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScanExtractionLine(
        String name,
        String quantity,
        String unit,
        @JsonProperty("unit_price") String unitPrice,
        @JsonProperty("written_line_total") String writtenLineTotal,
        @JsonProperty("confidence_name") Double confidenceName,
        @JsonProperty("confidence_quantity") Double confidenceQuantity,
        @JsonProperty("confidence_unit_price") Double confidenceUnitPrice
) {}
