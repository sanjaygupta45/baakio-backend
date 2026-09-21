package com.baakio.scan.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanDraftFactoryTest {
    private final ScanDraftFactory factory = new ScanDraftFactory();

    @Test
    void calculatesTotalsFromValidatedLinesAndFlagsWrittenMismatch() {
        ScanExtraction extraction = new ScanExtraction(true, "2026-09-20", "New purchase", "0.00", "31.00",
                List.of(new ScanExtractionLine("चना", "2", "kg", "10.00", "20.00", 0.98, 0.98, 0.99),
                        new ScanExtractionLine("चीनी", "1", "kg", "12.00", "12.00", 0.99, 0.98, 0.98)));
        var draft = factory.create(extraction);
        assertEquals("32.00", draft.calculatedTotal());
        assertEquals("31.00", draft.writtenTotal());
        assertTrue(draft.flags().contains("TOTAL_MISMATCH"));
        assertEquals("चना", draft.lines().getFirst().name());
    }

    @Test
    void unreadableValuesStayBlankAndAreFlaggedInsteadOfGuessed() {
        ScanExtraction extraction = new ScanExtraction(true, null, null, null, "50",
                List.of(new ScanExtractionLine("आटा", "not sure", "kg", null, null, 0.99, 0.2, 0.1)));
        var draft = factory.create(extraction);
        assertEquals("", draft.lines().getFirst().quantity());
        assertEquals("", draft.lines().getFirst().unitPrice());
        assertEquals("", draft.calculatedTotal());
        assertTrue(draft.flags().contains("UNCERTAIN_QUANTITY"));
        assertTrue(draft.flags().contains("UNCERTAIN_UNIT_PRICE"));
        assertTrue(draft.flags().contains("UNCERTAIN_DATE"));
    }
}
