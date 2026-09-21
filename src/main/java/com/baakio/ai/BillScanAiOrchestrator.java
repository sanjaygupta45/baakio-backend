package com.baakio.ai;

import com.baakio.exception.ApiException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Coordinates model extraction, bounded retries, field validation, and draft preparation. */
@Component
public class BillScanAiOrchestrator {
    private final ScanExtractor extractor;
    private final ScanDraftFactory drafts;
    private final ObjectMapper mapper;

    public BillScanAiOrchestrator(ScanExtractor extractor, ScanDraftFactory drafts, ObjectMapper mapper) {
        this.extractor = extractor;
        this.drafts = drafts;
        this.mapper = mapper;
    }

    public ScanAiResult prepareDraft(byte[] image, String mimeType) throws InterruptedException {
        ScanExtraction extraction = extractWithRetry(image, mimeType);
        ScanDraft draft = drafts.create(extraction);
        try {
            return new ScanAiResult(mapper.writeValueAsString(extraction), draft,
                    mapper.writeValueAsString(draft.flags()), decimal(draft.writtenTotal()),
                    decimal(draft.calculatedTotal()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("AI bill draft could not be serialized.", exception);
        }
    }

    private ScanExtraction extractWithRetry(byte[] image, String mimeType) throws InterruptedException {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return extractor.extract(image, mimeType);
            } catch (ApiException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                last = exception;
                if (attempt < 2) Thread.sleep(attempt == 0 ? 500 : 1500);
            }
        }
        throw last == null ? new IllegalStateException("Mistral OCR did not return a draft.") : last;
    }

    private BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
