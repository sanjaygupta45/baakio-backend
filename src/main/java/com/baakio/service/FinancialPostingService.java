package com.baakio.service;

import com.baakio.dto.ledger.AllocationResponse;
import com.baakio.dto.ledger.CreatePaymentRequest;
import com.baakio.dto.ledger.CreatePurchaseRequest;
import com.baakio.dto.ledger.OpeningBalanceRequest;
import com.baakio.dto.ledger.OpeningBalanceResponse;
import com.baakio.dto.ledger.PaymentAllocationRequest;
import com.baakio.dto.ledger.PaymentResponse;
import com.baakio.dto.ledger.PurchaseLineRequest;
import com.baakio.dto.ledger.PurchaseLineResponse;
import com.baakio.dto.ledger.PurchaseResponse;
import com.baakio.dto.ledger.AllocationResponse;
import com.baakio.model.AccountOpeningBalance;
import com.baakio.model.Payment;
import com.baakio.model.Purchase;
import com.baakio.model.PurchaseLine;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.repository.FinancialRepository;
import com.baakio.repository.LedgerRepository;
import com.baakio.exception.ApiException;
import com.baakio.utils.MoneyAmounts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

@Service
public class FinancialPostingService {
    private static final Set<String> METHODS = Set.of("CASH", "UPI", "BANK", "OTHER");
    private final FinancialRepository financial;
    private final LedgerRepository ledger;
    private final IdempotencyService idempotency;

    public FinancialPostingService(FinancialRepository financial, LedgerRepository ledger,
                                   IdempotencyService idempotency) {
        this.financial = financial;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    @Transactional
    public OpeningBalanceResponse postOpeningBalance(AccountHolderPrincipal principal, long customerId,
            OpeningBalanceRequest request, String key) {
        validateKey(key);
        ledger.lockCustomer(principal.businessId(), customerId);
        OptionalLong prior = idempotency.existing(principal.businessId(), key, "OPENING_BALANCE");
        if (prior.isPresent()) return openingResponse(principal.businessId(), customerId, prior.getAsLong());
        if (!Boolean.TRUE.equals(request.acknowledged())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "OPENING_ACKNOWLEDGEMENT_REQUIRED", "Confirm that this is the balance at the selected cutoff date.");
        }
        LocalDate today = ledger.businessDate(principal.businessId());
        if (request.cutoffDate().isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPENING_DATE_IN_FUTURE", "The cutoff date cannot be in the future.");
        }
        if (financial.hasOpeningBalance(principal.businessId(), customerId)) {
            throw new ApiException(HttpStatus.CONFLICT, "OPENING_BALANCE_ALREADY_SET", "An opening balance is already recorded for this customer.");
        }
        if (financial.hasActivityOnOrBefore(principal.businessId(), customerId, request.cutoffDate())) {
            throw new ApiException(HttpStatus.CONFLICT, "OPENING_BALANCE_DATE_CONFLICT", "Recorded activity exists on or before this cutoff date. Choose a later cutoff and reconcile the account.");
        }
        BigDecimal amount = MoneyAmounts.requireCents(request.amount(), "INVALID_OPENING_AMOUNT");
        long id = financial.insertOpeningBalance(principal.businessId(), customerId, amount, request.cutoffDate(), principal.id());
        idempotency.record(principal.businessId(), key, "OPENING_BALANCE", "OPENING_BALANCE", id);
        audit(principal, "OPENING_BALANCE", id, "POSTED");
        return openingResponse(principal.businessId(), customerId, id);
    }

    @Transactional
    public PurchaseResponse postPurchase(AccountHolderPrincipal principal, long customerId,
            CreatePurchaseRequest request, String key) {
        validateKey(key);
        ledger.lockCustomer(principal.businessId(), customerId);
        OptionalLong prior = idempotency.existing(principal.businessId(), key, "PURCHASE");
        if (prior.isPresent()) return purchaseResponse(principal.businessId(), customerId, prior.getAsLong());

        LocalDate occurredOn = request.occurredOn() == null ? ledger.businessDate(principal.businessId()) : request.occurredOn();
        ensureDateAllowed(principal.businessId(), customerId, occurredOn);
        String mode = request.mode();
        BigDecimal subtotal;
        BigDecimal discount = MoneyAmounts.nonNegativeCents(request.discount() == null ? MoneyAmounts.zero() : request.discount(), "INVALID_DISCOUNT");
        List<PreparedLine> lines = new ArrayList<>();
        String description = clean(request.description());

        if ("QUICK".equals(mode)) {
            if (request.amount() == null || (request.lines() != null && !request.lines().isEmpty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "QUICK_PURCHASE_FIELDS", "Enter one quick purchase amount without item lines.");
            }
            subtotal = MoneyAmounts.requireCents(request.amount(), "INVALID_PURCHASE_AMOUNT");
            if (discount.signum() != 0) throw new ApiException(HttpStatus.BAD_REQUEST, "QUICK_PURCHASE_DISCOUNT", "Discounts can only be entered with itemized purchases.");
        } else if ("ITEMIZED".equals(mode)) {
            if (request.amount() != null || request.lines() == null || request.lines().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ITEMIZED_PURCHASE_FIELDS", "Add at least one item line and do not send a separate total.");
            }
            subtotal = MoneyAmounts.zero();
            int lineNo = 1;
            for (PurchaseLineRequest line : request.lines()) {
                if (line.quantity().scale() > 3 || line.quantity().signum() <= 0 || line.quantity().precision() - line.quantity().scale() > 15) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Item quantities must be positive and have at most three decimal places.");
                }
                BigDecimal unitPrice = MoneyAmounts.nonNegativeCents(line.unitPrice(), "INVALID_UNIT_PRICE");
                BigDecimal lineTotal = MoneyAmounts.nonNegativeCents(
                        MoneyAmounts.lineTotal(line.quantity(), unitPrice), "PURCHASE_TOTAL_TOO_LARGE");
                subtotal = subtotal.add(lineTotal);
                MoneyAmounts.nonNegativeCents(subtotal, "PURCHASE_TOTAL_TOO_LARGE");
                lines.add(new PreparedLine(lineNo++, line.name().trim(), line.quantity().stripTrailingZeros(), clean(line.unit()), unitPrice, lineTotal));
            }
            subtotal = subtotal.setScale(2);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PURCHASE_MODE", "Choose a quick or itemized purchase.");
        }

        BigDecimal total = subtotal.subtract(discount).setScale(2);
        if (total.signum() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "PURCHASE_TOTAL_INVALID", "The purchase total after discount must be greater than zero.");
        BigDecimal paidNow = null;
        String paidMethod = null;
        if (request.paidNow() != null) {
            if (!Boolean.TRUE.equals(request.paidNow().confirmedByAccountHolder())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "PAYMENT_CONFIRMATION_REQUIRED", "Confirm that the customer paid this amount now.");
            }
            paidNow = MoneyAmounts.requireCents(request.paidNow().amount(), "INVALID_PAYMENT_AMOUNT");
            paidMethod = validateMethod(request.paidNow().method());
            if (paidNow.compareTo(total) > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_PURCHASE", "The payment at purchase time cannot exceed the purchase total.");
            }
        }

        long purchaseId = financial.insertPurchase(new Purchase(principal.businessId(), customerId, occurredOn,
                mode, description, subtotal, discount, total, principal.id()));
        for (PreparedLine line : lines) {
            financial.insertPurchaseLine(new PurchaseLine(purchaseId, line.lineNo(), line.name(),
                    line.quantity(), line.unit(), line.unitPrice(), line.lineTotal()));
        }
        if (paidNow != null) insertPayment(principal, customerId, purchaseId, paidNow, paidMethod, occurredOn, null);
        idempotency.record(principal.businessId(), key, "PURCHASE", "PURCHASE", purchaseId);
        audit(principal, "PURCHASE", purchaseId, "POSTED");
        return purchaseResponse(principal.businessId(), customerId, purchaseId);
    }

    @Transactional
    public PaymentResponse postPayment(AccountHolderPrincipal principal, long customerId,
            CreatePaymentRequest request, String key) {
        validateKey(key);
        ledger.lockCustomer(principal.businessId(), customerId);
        OptionalLong prior = idempotency.existing(principal.businessId(), key, "PAYMENT");
        if (prior.isPresent()) return paymentResponse(principal.businessId(), customerId, prior.getAsLong());
        if (!Boolean.TRUE.equals(request.confirmedByAccountHolder())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "PAYMENT_CONFIRMATION_REQUIRED", "Confirm that this payment was received by the shop.");
        }
        LocalDate occurredOn = request.occurredOn() == null ? ledger.businessDate(principal.businessId()) : request.occurredOn();
        ensureDateAllowed(principal.businessId(), customerId, occurredOn);
        BigDecimal amount = MoneyAmounts.requireCents(request.amount(), "INVALID_PAYMENT_AMOUNT");
        String method = validateMethod(request.method());
        BigDecimal current = ledger.outstanding(principal.businessId(), customerId);
        if (amount.compareTo(current) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_BALANCE", "This payment is greater than the current outstanding balance.");
        }
        BigDecimal availableOnDate = ledger.outstandingThrough(principal.businessId(), customerId, occurredOn);
        if (amount.compareTo(availableOnDate) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_DATE_BALANCE", "This payment is greater than the outstanding balance on the selected payment date.");
        }
        List<PaymentAllocationRequest> allocations = request.allocations() == null ? List.of() : request.allocations();
        BigDecimal allocationTotal = MoneyAmounts.zero();
        for (PaymentAllocationRequest allocation : allocations) {
            allocationTotal = allocationTotal.add(MoneyAmounts.requireCents(allocation.amount(), "INVALID_ALLOCATION"));
        }
        if (allocationTotal.compareTo(amount) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ALLOCATIONS_EXCEED_PAYMENT", "Promise allocations cannot exceed the received payment.");
        }
        if (allocations.stream().map(PaymentAllocationRequest::commitmentId).distinct().count() != allocations.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_ALLOCATION", "Each promise can appear only once in a payment.");
        }
        for (PaymentAllocationRequest allocation : allocations) {
            BigDecimal remaining = commitmentRemaining(principal.businessId(), customerId, allocation.commitmentId());
            BigDecimal allocationAmount = MoneyAmounts.requireCents(allocation.amount(), "INVALID_ALLOCATION");
            if (allocationAmount.compareTo(remaining) > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "ALLOCATION_EXCEEDS_PROMISE", "A promise allocation cannot exceed its remaining amount.");
            }
        }

        long paymentId = insertPayment(principal, customerId, null, amount, method, occurredOn, clean(request.reference()));
        idempotency.record(principal.businessId(), key, "PAYMENT", "PAYMENT", paymentId);
        // Allocations fulfill promises; the confirmed payment already reduced the account once.
        for (PaymentAllocationRequest allocation : allocations) {
            financial.insertAllocation(principal.businessId(), paymentId, allocation.commitmentId(),
                    MoneyAmounts.requireCents(allocation.amount(), "INVALID_ALLOCATION"));
            BigDecimal remaining = commitmentRemaining(principal.businessId(), customerId, allocation.commitmentId());
            if (remaining.signum() == 0) {
                financial.markCommitmentFulfilled(principal.businessId(), customerId, allocation.commitmentId());
            }
        }
        audit(principal, "PAYMENT", paymentId, "CONFIRMED");
        return paymentResponse(principal.businessId(), customerId, paymentId);
    }

    public OpeningBalanceResponse openingResponse(String businessId, long customerId, long openingId) {
        AccountOpeningBalance opening = financial.findOpeningBalance(businessId, customerId, openingId);
        if (opening == null) throw missingIdempotentResource();
        return new OpeningBalanceResponse(customerId, MoneyAmounts.text(opening.getAmount()), opening.getCutoffDate(),
                MoneyAmounts.text(ledger.outstanding(businessId, customerId)), opening.getPostedAt());
    }

    public PurchaseResponse purchaseResponse(String businessId, long customerId, long purchaseId) {
        Purchase purchase = financial.findPurchase(businessId, customerId, purchaseId);
        if (purchase == null) throw missingIdempotentResource();
        List<PurchaseLineResponse> lines = financial.findPurchaseLines(purchaseId).stream().map(line ->
                new PurchaseLineResponse(line.getName(), line.getQuantity().stripTrailingZeros().toPlainString(),
                        line.getUnit(), MoneyAmounts.text(line.getUnitPrice()), MoneyAmounts.text(line.getLineTotal()))).toList();
        return new PurchaseResponse(purchaseId, customerId, purchase.getEntryMode(), purchase.getOccurredOn(),
                purchase.getDescription(), MoneyAmounts.text(purchase.getSubtotal()), MoneyAmounts.text(purchase.getDiscount()),
                MoneyAmounts.text(purchase.getTotal()), MoneyAmounts.text(financial.purchasePaymentTotal(purchaseId)),
                MoneyAmounts.text(ledger.outstanding(businessId, customerId)), lines, purchase.getPostedAt());
    }

    public PaymentResponse paymentResponse(String businessId, long customerId, long paymentId) {
        Payment payment = financial.findPayment(businessId, customerId, paymentId);
        if (payment == null) throw missingIdempotentResource();
        List<AllocationResponse> allocations = financial.findPaymentAllocations(paymentId);
        return new PaymentResponse(paymentId, customerId, MoneyAmounts.text(payment.getAmount()),
                payment.getMethod(), payment.getOccurredOn(), payment.getReferenceText(),
                MoneyAmounts.text(ledger.outstanding(businessId, customerId)), allocations, payment.getPostedAt());
    }

    private long insertPayment(AccountHolderPrincipal principal, long customerId, Long purchaseId, BigDecimal amount,
            String method, LocalDate occurredOn, String reference) {
        return financial.insertPayment(principal.businessId(), customerId, purchaseId, amount,
                method, occurredOn, reference, principal.id());
    }

    private BigDecimal commitmentRemaining(String businessId, long customerId, long commitmentId) {
        BigDecimal remaining = financial.commitmentRemaining(businessId, customerId, commitmentId);
        if (remaining == null) throw ApiException.notFound();
        return remaining;
    }

    private void ensureDateAllowed(String businessId, long customerId, LocalDate date) {
        if (date.isAfter(ledger.businessDate(businessId))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRANSACTION_DATE_IN_FUTURE", "The transaction date cannot be in the future.");
        }
        LocalDate cutoff = financial.findOpeningCutoff(businessId, customerId);
        if (cutoff != null && !date.isAfter(cutoff)) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSACTION_BEFORE_OPENING_CUTOFF", "This date is on or before the opening-balance cutoff. Review the account migration date.");
        }
    }

    private String validateMethod(String method) {
        String normalized = method == null ? "" : method.trim().toUpperCase();
        if (!METHODS.contains(normalized)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD", "Choose cash, UPI, bank, or other.");
        return normalized;
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Provide a valid Idempotency-Key to safely save this action.");
        }
    }

    private void audit(AccountHolderPrincipal principal, String type, long id, String action) {
        financial.audit(principal.businessId(), principal.id(), type, id, action);
    }

    private ApiException missingIdempotentResource() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_RESOURCE_MISSING", "The earlier request could not be retrieved. Refresh the account.");
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record PreparedLine(int lineNo, String name, BigDecimal quantity, String unit, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
