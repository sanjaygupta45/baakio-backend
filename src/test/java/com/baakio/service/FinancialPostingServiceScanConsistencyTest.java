package com.baakio.service;

import com.baakio.dto.ledger.CreatePurchaseRequest;
import com.baakio.dto.ledger.PurchaseLineRequest;
import com.baakio.model.Purchase;
import com.baakio.model.PurchaseLine;
import com.baakio.repository.AccountOpeningBalanceRepository;
import com.baakio.repository.AuditEventRepository;
import com.baakio.repository.CommitmentRepository;
import com.baakio.repository.BusinessRepository;
import com.baakio.repository.CustomerRepository;
import com.baakio.repository.PaymentAllocationRepository;
import com.baakio.repository.PaymentRepository;
import com.baakio.repository.PurchaseLineRepository;
import com.baakio.repository.PurchaseRepository;
import com.baakio.security.AccountHolderPrincipal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialPostingServiceScanConsistencyTest {
    @Test
    void confirmedScanLinesUseExistingRoundingAndOnlyCreateAStandardPurchase() {
        LocalDate date = LocalDate.of(2026, 9, 20);
        Purchase[] savedPurchase = new Purchase[1];
        List<PurchaseLine> savedLines = new ArrayList<>();
        String[] auditAction = new String[1];
        AccountOpeningBalanceRepository openings = proxy(AccountOpeningBalanceRepository.class,
                (proxy, method, args) -> method.getName().equals("findByBusinessIdAndCustomerId")
                        ? Optional.empty() : defaultValue(method.getReturnType()));
        AuditEventRepository audits = proxy(AuditEventRepository.class, (proxy, method, args) -> {
            if (method.getName().equals("save")) {
                auditAction[0] = ((com.baakio.model.AuditEvent) args[0]).getAction();
                return args[0];
            }
            return defaultValue(method.getReturnType());
        });
        CommitmentRepository commitments = proxy(CommitmentRepository.class,
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        PaymentAllocationRepository allocations = proxy(PaymentAllocationRepository.class,
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        PaymentRepository payments = proxy(PaymentRepository.class, (proxy, method, args) -> {
            if (method.getName().equals("sumForPurchase")) return BigDecimal.ZERO.setScale(2);
            if (method.getName().equals("sumConfirmed")) return BigDecimal.ZERO.setScale(2);
            return defaultValue(method.getReturnType());
        });
        PurchaseLineRepository purchaseLines = proxy(PurchaseLineRepository.class, (proxy, method, args) -> {
            if (method.getName().equals("save")) {
                savedLines.add((PurchaseLine) args[0]);
                return args[0];
            }
            if (method.getName().equals("findByPurchaseIdOrderByLineNo")) return savedLines;
            return defaultValue(method.getReturnType());
        });
        PurchaseRepository purchases = proxy(PurchaseRepository.class, (proxy, method, args) -> {
            if (method.getName().equals("saveAndFlush")) {
                savedPurchase[0] = (Purchase) args[0];
                assignId(savedPurchase[0], 44L);
                return savedPurchase[0];
            }
            if (method.getName().equals("sumTotal")) return new BigDecimal("3.33");
            if (method.getName().equals("findByBusinessIdAndCustomerIdAndId")) return Optional.of(savedPurchase[0]);
            return defaultValue(method.getReturnType());
        });
        BusinessRepository businesses = proxy(BusinessRepository.class, (proxy, method, args) ->
                method.getName().equals("findById")
                        ? Optional.of(new com.baakio.model.Business("business", "Shop", "owner"))
                        : defaultValue(method.getReturnType()));
        CustomerRepository customers = proxy(CustomerRepository.class, (proxy, method, args) ->
                method.getName().equals("findLockedByBusinessIdAndId")
                        ? Optional.of(new com.baakio.model.Customer("business", "Customer", null, null, "en-IN"))
                        : defaultValue(method.getReturnType()));
        LedgerService ledger = new LedgerService(openings, businesses, commitments, customers, payments, purchases);
        FakeIdempotencyService idempotency = new FakeIdempotencyService();
        AccountHolderPrincipal owner = new AccountHolderPrincipal("owner", "business", "Shop", "Owner",
                "+919876543210", null, true);
        CreatePurchaseRequest request = new CreatePurchaseRequest("ITEMIZED", date, "Bill", null, BigDecimal.ZERO,
                List.of(new PurchaseLineRequest("Flour", new BigDecimal("0.333"), "kg", new BigDecimal("10.01"))), null);

        var result = new FinancialPostingService(openings, audits, commitments, allocations, payments,
                purchaseLines, purchases, ledger, idempotency)
                .postPurchase(owner, 9, request, "scan.purchase.17");

        assertEquals(new BigDecimal("3.33"), savedPurchase[0].getTotal());
        assertEquals(new BigDecimal("3.33"), savedLines.getFirst().getLineTotal());
        assertEquals("scan.purchase.17", idempotency.recordedKey);
        assertEquals("POSTED", auditAction[0]);
        assertEquals("3.33", result.total());
        assertEquals("3.33", result.outstanding());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (type == Optional.class) return Optional.empty();
            if (type == List.class) return List.of();
            return null;
        }
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }

    private static void assignId(Purchase purchase, long id) {
        try {
            Field field = Purchase.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(purchase, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FakeIdempotencyService extends IdempotencyService {
        private String recordedKey;
        private FakeIdempotencyService() { super(null); }
        @Override public OptionalLong existing(String businessId, String key, String operation) { return OptionalLong.empty(); }
        @Override public void record(String businessId, String key, String operation, String resourceType, long resourceId) { recordedKey = key; }
    }
}
