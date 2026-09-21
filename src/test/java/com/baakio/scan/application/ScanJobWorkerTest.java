package com.baakio.scan.application;

import com.baakio.scan.ai.ScanDraftFactory;
import com.baakio.scan.ai.ScanExtractor;
import com.baakio.scan.persistence.ScanClaim;
import com.baakio.scan.persistence.ScanDraft;
import com.baakio.scan.persistence.ScanJobRecord;
import com.baakio.scan.persistence.ScanRepository;
import com.baakio.storage.DocumentStorage;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanJobWorkerTest {
    @Test
    void imageRetrievalFailureMarksScanFailedWithoutPostingFinancialRecords() {
        ScanClaim claim = claim(81);
        FakeRepository scans = new FakeRepository(claim);
        DocumentStorage storage = new DocumentStorage() {
            @Override public void put(String objectKey, byte[] content, String contentType) {}
            @Override public StoredDocument get(String objectKey) { throw new IllegalStateException("storage unavailable"); }
            @Override public void delete(String objectKey) {}
        };
        ScanExtractor extractor = (content, mimeType) -> { throw new AssertionError("model must not be called"); };
        ScanJobWorker worker = new ScanJobWorker(scans, storage, extractor, new ScanDraftFactory(), mapper());

        worker.process(81);

        assertEquals("OCR_FAILED", scans.failureCode);
        assertEquals(0, scans.savedDrafts);
    }

    @Test
    void duplicateQueueDeliveryDoesNotReadImageOrCallTheModel() {
        FakeRepository scans = new FakeRepository(null);
        FakeStorage storage = new FakeStorage();
        ScanExtractor extractor = (content, mimeType) -> { throw new AssertionError("duplicate delivery must not call OCR"); };
        ScanJobWorker worker = new ScanJobWorker(scans, storage, extractor, new ScanDraftFactory(), mapper());

        worker.process(82);

        assertEquals(0, storage.reads);
        assertEquals(0, scans.savedDrafts);
    }

    private ObjectMapper mapper() { return JsonMapper.builder().findAndAddModules().build(); }

    private ScanClaim claim(long id) {
        ScanJobRecord job = new ScanJobRecord(id, "business", 7, "object-key", "hash", "image/png",
                "PROCESSING", 1, null, "upload-key", false, Instant.now(), Instant.now(), "claim-token", null);
        return new ScanClaim(job, "claim-token", 1);
    }

    private static final class FakeRepository extends ScanRepository {
        private final ScanClaim claim;
        private String failureCode;
        private int savedDrafts;
        private FakeRepository(ScanClaim claim) { super(null, JsonMapper.builder().findAndAddModules().build()); this.claim = claim; }
        @Override public Optional<ScanClaim> claim(long id) { return Optional.ofNullable(claim); }
        @Override public boolean saveDraft(ScanClaim claim, String extractedJson, ScanDraft draft,
                                           String flagsJson, BigDecimal writtenTotal, BigDecimal calculatedTotal) {
            savedDrafts++; return true;
        }
        @Override public void markFailed(ScanClaim claim, String errorCode) { failureCode = errorCode; }
    }

    private static final class FakeStorage implements DocumentStorage {
        private int reads;
        @Override public void put(String objectKey, byte[] content, String contentType) {}
        @Override public StoredDocument get(String objectKey) { reads++; return new StoredDocument(new byte[0], "image/png"); }
        @Override public void delete(String objectKey) {}
    }
}
