package com.baakio.ai;

import com.baakio.ai.ScanDraft;

import java.math.BigDecimal;

public record ScanAiResult(String extractedJson, ScanDraft draft, String flagsJson,
                           BigDecimal writtenTotal, BigDecimal calculatedTotal) {}
