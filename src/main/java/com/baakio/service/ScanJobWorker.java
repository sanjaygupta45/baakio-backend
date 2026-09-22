package com.baakio.service;

import com.baakio.ai.BillScanAiOrchestrator;
import com.baakio.ai.ScanAiResult;
import com.baakio.dto.scan.ScanClaim;
import com.baakio.dto.scan.ScanJobRecord;
import com.baakio.dto.scan.StoredDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ScanJobWorker {
    private static final Logger log = LoggerFactory.getLogger(ScanJobWorker.class);
    private final ScanPersistenceService scans;
    private final DocumentStorage storage;
    private final BillScanAiOrchestrator ai;

    public ScanJobWorker(ScanPersistenceService scans, DocumentStorage storage, BillScanAiOrchestrator ai) {
        this.scans = scans;
        this.storage = storage;
        this.ai = ai;
    }

    public void process(long scanId) {
        Optional<ScanClaim> claimed = scans.claim(scanId);
        if (claimed.isEmpty()) return;
        ScanClaim claim = claimed.get();
        try {
            StoredDocument document = storage.get(claim.job().objectKey());
            ScanAiResult draft = ai.prepareDraft(document.content(), claim.job().mimeType());
            scans.saveDraft(claim, draft.extractedJson(), draft.draft(), draft.flagsJson(),
                    draft.writtenTotal(), draft.calculatedTotal());
        } catch (com.baakio.exception.ApiException exception) {
            scans.markFailed(claim, exception.code());
        } catch (Exception exception) {
            log.warn("Bill scan {} failed during extraction ({})", scanId, exception.getClass().getSimpleName());
            scans.markFailed(claim, "OCR_FAILED");
        }
    }

}
