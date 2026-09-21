package com.baakio.scan.application;

import com.baakio.scan.ai.ScanDraftFactory;
import com.baakio.scan.ai.ScanExtraction;
import com.baakio.scan.ai.ScanExtractor;
import com.baakio.scan.persistence.ScanClaim;
import com.baakio.scan.persistence.ScanDraft;
import com.baakio.scan.persistence.ScanRepository;
import com.baakio.storage.DocumentStorage;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class ScanJobWorker {
    private static final Logger log = LoggerFactory.getLogger(ScanJobWorker.class);
    private final ScanRepository scans;
    private final DocumentStorage storage;
    private final ScanExtractor extractor;
    private final ScanDraftFactory drafts;
    private final ObjectMapper mapper;

    public ScanJobWorker(ScanRepository scans, DocumentStorage storage, ScanExtractor extractor,
                         ScanDraftFactory drafts, ObjectMapper mapper) {
        this.scans = scans;
        this.storage = storage;
        this.extractor = extractor;
        this.drafts = drafts;
        this.mapper = mapper;
    }

    public void process(long scanId) {
        Optional<ScanClaim> claimed = scans.claim(scanId);
        if (claimed.isEmpty()) return;
        ScanClaim claim = claimed.get();
        try {
            DocumentStorage.StoredDocument document = storage.get(claim.job().objectKey());
            ScanExtraction extraction = extractWithRetry(document.content(), claim.job().mimeType());
            ScanDraft draft = drafts.create(extraction);
            String extractionJson = mapper.writeValueAsString(extraction);
            scans.saveDraft(claim, extractionJson, draft, mapper.writeValueAsString(draft.flags()),
                    decimal(draft.writtenTotal()), decimal(draft.calculatedTotal()));
        } catch (com.baakio.exception.ApiException exception) {
            scans.markFailed(claim, exception.code());
        } catch (Exception exception) {
            log.warn("Bill scan {} failed during extraction ({})", scanId, exception.getClass().getSimpleName());
            scans.markFailed(claim, "OCR_FAILED");
        }
    }

    private ScanExtraction extractWithRetry(byte[] content, String mimeType) throws InterruptedException {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return extractor.extract(content, mimeType);
            } catch (RuntimeException exception) {
                last = exception;
                if (attempt < 2) Thread.sleep(attempt == 0 ? 500 : 1500);
            }
        }
        throw last == null ? new IllegalStateException("Mistral OCR did not return a draft.") : last;
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        return new BigDecimal(value);
    }
}
