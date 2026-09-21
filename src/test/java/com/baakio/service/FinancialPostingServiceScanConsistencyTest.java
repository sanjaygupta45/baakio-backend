package com.baakio.service;

import com.baakio.dto.ledger.CreatePurchaseRequest;
import com.baakio.dto.ledger.PurchaseLineRequest;
import com.baakio.model.Purchase;
import com.baakio.model.PurchaseLine;
import com.baakio.repository.FinancialRepository;
import com.baakio.repository.LedgerRepository;
import com.baakio.security.AccountHolderPrincipal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialPostingServiceScanConsistencyTest {
    @Test
    void confirmedScanLinesUseExistingRoundingAndOnlyCreateAStandardPurchase() {
        LocalDate date = LocalDate.of(2026, 9, 20);
        FakeFinancialRepository financial = new FakeFinancialRepository(date);
        FakeLedgerRepository ledger = new FakeLedgerRepository(date);
        FakeIdempotencyService idempotency = new FakeIdempotencyService();
        AccountHolderPrincipal owner = new AccountHolderPrincipal("owner", "business", "Shop", "Owner",
                "+919876543210", null, true);
        CreatePurchaseRequest request = new CreatePurchaseRequest("ITEMIZED", date, "Bill", null, BigDecimal.ZERO,
                List.of(new PurchaseLineRequest("Flour", new BigDecimal("0.333"), "kg", new BigDecimal("10.01"))), null);

        var result = new FinancialPostingService(financial, ledger, idempotency)
                .postPurchase(owner, 9, request, "scan.purchase.17");

        assertEquals(new BigDecimal("3.33"), financial.purchase.getTotal());
        assertEquals(new BigDecimal("3.33"), financial.savedLines.getFirst().getLineTotal());
        assertEquals(0, financial.paymentWrites);
        assertEquals("scan.purchase.17", idempotency.recordedKey);
        assertEquals("POSTED", financial.auditAction);
        assertEquals("3.33", result.total());
        assertEquals("3.33", result.outstanding());
    }

    private static final class FakeFinancialRepository extends FinancialRepository {
        private final LocalDate date;
        private Purchase purchase;
        private final List<PurchaseLine> savedLines = new ArrayList<>();
        private int paymentWrites;
        private String auditAction;

        private FakeFinancialRepository(LocalDate date) { this.date = date; }
        @Override public LocalDate findOpeningCutoff(String businessId, long customerId) { return null; }
        @Override public long insertPurchase(Purchase purchase) { this.purchase = purchase; return 44L; }
        @Override public void insertPurchaseLine(PurchaseLine line) { savedLines.add(line); }
        @Override public long insertPayment(String businessId, long customerId, Long purchaseId, BigDecimal amount,
                                             String method, LocalDate occurredOn, String reference, String userId) {
            paymentWrites++; return 1L;
        }
        @Override public Purchase findPurchase(String businessId, long customerId, long purchaseId) { return purchase; }
        @Override public List<PurchaseLine> findPurchaseLines(long purchaseId) { return savedLines; }
        @Override public BigDecimal purchasePaymentTotal(long purchaseId) { return BigDecimal.ZERO.setScale(2); }
        @Override public void audit(String businessId, String userId, String entityType, long entityId, String action) { auditAction = action; }
    }

    private static final class FakeLedgerRepository extends LedgerRepository {
        private final LocalDate date;
        private FakeLedgerRepository(LocalDate date) { this.date = date; }
        @Override public void lockCustomer(String businessId, long customerId) {}
        @Override public LocalDate businessDate(String businessId) { return date; }
        @Override public BigDecimal outstanding(String businessId, long customerId) { return new BigDecimal("3.33"); }
    }

    private static final class FakeIdempotencyService extends IdempotencyService {
        private String recordedKey;
        private FakeIdempotencyService() { super(null); }
        @Override public OptionalLong existing(String businessId, String key, String operation) { return OptionalLong.empty(); }
        @Override public void record(String businessId, String key, String operation, String resourceType, long resourceId) { recordedKey = key; }
    }
}
