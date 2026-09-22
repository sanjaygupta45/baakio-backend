package com.baakio.service;

import com.baakio.dto.ledger.CreatePurchaseRequest;
import com.baakio.dto.ledger.PurchaseLineRequest;
import com.baakio.dto.ledger.PurchaseResponse;
import com.baakio.exception.ApiException;
import com.baakio.dto.scan.ScanConfirmRequest;
import com.baakio.ai.ScanDraft;
import com.baakio.dto.scan.ScanDraftRecord;
import com.baakio.dto.scan.ScanJobRecord;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.service.CustomerService;
import com.baakio.service.FinancialPostingService;
import com.baakio.service.IdempotencyService;
import com.baakio.dto.scan.StoredDocument;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillScanServiceTest {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final AccountHolderPrincipal owner = new AccountHolderPrincipal(
            "owner-1", "business-1", "Shop", "Owner", "+919876543210", null, true);

    @Test
    void anotherBusinessCannotReadScanOrItsDraft() {
        FakeScanPersistenceService scans = new FakeScanPersistenceService(mapper);
        FakeStorage storage = new FakeStorage();
        ApiException error = assertThrows(ApiException.class, () -> service(scans, storage, new FakePosting(), new FakeIdempotency()).get(owner, 17));
        assertEquals(404, error.status().value());
        assertEquals(0, scans.draftReads);
        assertEquals(0, storage.reads);
    }

    @Test
    void confirmationRecomputesLinesAndPostsThroughTheFinancialServiceOnce() throws Exception {
        FakeScanPersistenceService scans = new FakeScanPersistenceService(mapper);
        scans.job = job(17, "REVIEW_REQUIRED");
        ScanDraft savedDraft = new ScanDraft(LocalDate.of(2026, 9, 20), "Bill", "0.00", "3.33", "3.33", List.of(), List.of());
        scans.draft = draftRecord(17, 3, null, mapper.writeValueAsString(savedDraft));
        FakePosting postings = new FakePosting();
        FakeIdempotency idempotency = new FakeIdempotency();
        PurchaseResponse purchase = purchaseResponse(44, 9);
        postings.purchaseResult = purchase;
        ScanConfirmRequest request = new ScanConfirmRequest(3L, LocalDate.of(2026, 9, 20), "Bill", BigDecimal.ZERO,
                List.of(new PurchaseLineRequest("Flour", new BigDecimal("0.333"), "kg", new BigDecimal("10.01"))), null);

        PurchaseResponse result = service(scans, new FakeStorage(), postings, idempotency).confirm(owner, 17, request, "confirm-key");

        assertEquals(44, result.id());
        assertEquals(1, postings.postCalls);
        assertEquals("ITEMIZED", postings.lastRequest.mode());
        assertEquals(new BigDecimal("0.333"), postings.lastRequest.lines().getFirst().quantity());
        assertEquals("scan.purchase.17", postings.lastKey);
        assertEquals(17, scans.postedScanId);
        assertEquals(44, scans.postedPurchaseId);
        assertEquals(3, scans.postedOldVersion);
        assertEquals(new BigDecimal("3.33"), scans.postedTotal);
        assertEquals("confirm-key", idempotency.recordedKey);
    }

    @Test
    void repeatConfirmationForPostedScanReturnsTheSamePurchaseWithoutPostingAgain() {
        FakeScanPersistenceService scans = new FakeScanPersistenceService(mapper);
        scans.job = job(17, "POSTED");
        scans.draft = draftRecord(17, 4, 44L, "{}");
        FakePosting postings = new FakePosting();
        postings.purchaseResult = purchaseResponse(44, 9);

        PurchaseResponse actual = service(scans, new FakeStorage(), postings, new FakeIdempotency()).confirm(owner, 17,
                new ScanConfirmRequest(3L, LocalDate.now(), null, BigDecimal.ZERO,
                        List.of(new PurchaseLineRequest("Flour", BigDecimal.ONE, null, BigDecimal.ONE)), null), "retry-key");

        assertEquals(44, actual.id());
        assertEquals(0, postings.postCalls);
        assertEquals(1, postings.purchaseResponseCalls);
    }

    private BillScanService service(FakeScanPersistenceService scans, FakeStorage storage,
                                           FakePosting postings, FakeIdempotency idempotency) {
        CustomerService customers = new CustomerService(null, null, null, null);
        return new BillScanService(scans, customers, storage, postings, idempotency, mapper);
    }

    private ScanJobRecord job(long id, String status) {
        return new ScanJobRecord(id, "business-1", 9, "private-key", "hash", "image/jpeg", status,
                1, null, "upload-key", false, Instant.now(), null, null, null);
    }

    private ScanDraftRecord draftRecord(long scanId, long version, Long purchaseId, String editedJson) {
        return new ScanDraftRecord(scanId, scanId, "{}", editedJson, "3.33", "3.33", "[]", version, purchaseId);
    }

    private PurchaseResponse purchaseResponse(long id, long customerId) {
        return new PurchaseResponse(id, customerId, "ITEMIZED", LocalDate.of(2026, 9, 20), "Bill",
                "3.33", "0.00", "3.33", "0.00", "3.33", List.of(), Instant.now());
    }

    private static final class FakeScanPersistenceService extends ScanPersistenceService {
        private ScanJobRecord job;
        private ScanDraftRecord draft;
        private int draftReads;
        private long postedScanId;
        private long postedPurchaseId;
        private long postedOldVersion;
        private BigDecimal postedTotal;

        private FakeScanPersistenceService(ObjectMapper mapper) { super(null, null, null, null, mapper); }

        @Override public Optional<ScanJobRecord> findForBusiness(String businessId, long id) {
            return job != null && job.id() == id && job.businessId().equals(businessId) ? Optional.of(job) : Optional.empty();
        }
        @Override public ScanDraftRecord findDraft(long scanId) { draftReads++; return draft; }
        @Override public ScanJobRecord lockForBusiness(String businessId, long id) {
            return findForBusiness(businessId, id).orElse(null);
        }
        @Override public ScanDraftRecord lockDraft(long scanId) { return draft; }
        @Override public void markPosted(long scanId, long oldVersion, long purchaseId, String editedJson, BigDecimal calculatedTotal) {
            postedScanId = scanId; postedOldVersion = oldVersion; postedPurchaseId = purchaseId; postedTotal = calculatedTotal;
        }
    }

    private static final class FakeStorage implements DocumentStorage {
        private int reads;
        @Override public void put(String objectKey, byte[] content, String contentType) {}
        @Override public StoredDocument get(String objectKey) { reads++; return new StoredDocument(new byte[0], "image/jpeg"); }
        @Override public void delete(String objectKey) {}
    }

    private static final class FakePosting extends FinancialPostingService {
        private int postCalls;
        private int purchaseResponseCalls;
        private CreatePurchaseRequest lastRequest;
        private String lastKey;
        private PurchaseResponse purchaseResult;
        private FakePosting() { super(null, null, null, null, null, null, null, null, null); }
        @Override public PurchaseResponse postPurchase(AccountHolderPrincipal principal, long customerId, CreatePurchaseRequest request, String key) {
            postCalls++; lastRequest = request; lastKey = key; return purchaseResult;
        }
        @Override public PurchaseResponse purchaseResponse(String businessId, long customerId, long purchaseId) {
            purchaseResponseCalls++; return purchaseResult;
        }
    }

    private static final class FakeIdempotency extends IdempotencyService {
        private String recordedKey;
        private FakeIdempotency() { super(null); }
        @Override public OptionalLong existing(String businessId, String key, String operation) { return OptionalLong.empty(); }
        @Override public void record(String businessId, String key, String operation, String resourceType, long resourceId) { recordedKey = key; }
    }
}
