package com.baakio.service;

import com.baakio.dto.ledger.LedgerMovement;
import com.baakio.dto.ledger.LedgerResponse;
import com.baakio.dto.ledger.PaymentHistoryResponse;
import com.baakio.dto.ledger.PurchasePage;
import com.baakio.model.AccountOpeningBalance;
import com.baakio.model.Payment;
import com.baakio.model.Purchase;
import com.baakio.repository.AccountOpeningBalanceRepository;
import com.baakio.repository.PaymentRepository;
import com.baakio.repository.PurchaseRepository;
import org.springframework.data.domain.PageRequest;
import com.baakio.exception.ApiException;
import com.baakio.utils.MoneyAmounts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LedgerReadService {
    private final PurchaseRepository purchases;
    private final PaymentRepository payments;
    private final AccountOpeningBalanceRepository openings;
    private final LedgerService ledger;
    private final FinancialPostingService postings;

    public LedgerReadService(PurchaseRepository purchases, PaymentRepository payments,
                             AccountOpeningBalanceRepository openings, LedgerService ledger,
                             FinancialPostingService postings) {
        this.purchases = purchases;
        this.payments = payments;
        this.openings = openings;
        this.ledger = ledger;
        this.postings = postings;
    }

    public LedgerResponse statement(String businessId, long customerId, LocalDate from, LocalDate to) {
        LocalDate today = ledger.businessDate(businessId);
        LocalDate periodEnd = to == null ? today : to;
        LocalDate periodStart = from == null ? periodEnd.withDayOfMonth(1) : from;
        if (periodStart.isAfter(periodEnd) || periodEnd.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATEMENT_RANGE", "Choose a date range ending today or earlier.");
        }

        BigDecimal opening = openingAt(businessId, customerId, periodStart);
        BigDecimal openingMovement = openingMovement(businessId, customerId, periodStart, periodEnd);
        BigDecimal purchases = this.purchases.sumBetween(businessId, customerId, periodStart, periodEnd);
        BigDecimal payments = this.payments.sumBetween(businessId, customerId, periodStart, periodEnd);
        BigDecimal openingInPeriod = openingMovement;
        List<LedgerMovement> movements = new ArrayList<>();
        if (openingMovement.signum() > 0) {
            LocalDate cutoff = openingCutoff(businessId, customerId);
            if (cutoff != null && !cutoff.isBefore(periodStart) && !cutoff.isAfter(periodEnd)) {
                Instant postedAt = openingTimestamp(businessId, customerId);
                movements.add(new LedgerMovement("OPENING_BALANCE", 0,
                        cutoff, "Opening balance", MoneyAmounts.text(openingMovement), "DEBIT", null, postedAt));
            }
        }
        for (Purchase purchase : this.purchases.findBetween(businessId, customerId, periodStart, periodEnd)) {
            movements.add(new LedgerMovement("PURCHASE", purchase.getId(), purchase.getOccurredOn(),
                    purchase.getDescription(), MoneyAmounts.text(purchase.getTotal()), "DEBIT", null,
                    purchase.getPostedAt()));
        }
        for (Payment payment : this.payments.findBetween(businessId, customerId, periodStart, periodEnd)) {
            movements.add(new LedgerMovement("PAYMENT", payment.getId(), payment.getOccurredOn(),
                    payment.getReferenceText(), MoneyAmounts.text(payment.getAmount()), "CREDIT",
                    payment.getMethod(), payment.getPostedAt()));
        }
        movements.sort(Comparator.comparing(LedgerMovement::occurredOn)
                .thenComparing(LedgerMovement::postedAt)
                .thenComparing(LedgerMovement::type)
                .thenComparingLong(LedgerMovement::id));
        BigDecimal closing = opening.add(openingInPeriod).add(purchases).subtract(payments).setScale(2);
        return new LedgerResponse(customerId, periodStart, periodEnd, MoneyAmounts.text(opening),
                MoneyAmounts.text(purchases), MoneyAmounts.text(payments), MoneyAmounts.text(closing),
                MoneyAmounts.text(ledger.outstanding(businessId, customerId)), movements);
    }

    public PurchasePage purchases(String businessId, long customerId, LocalDate from, LocalDate to, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, 50));
        long count = purchases.countForPeriod(businessId, customerId, from, to);
        List<Long> ids = purchases.findIdsForPeriod(businessId, customerId, from, to, PageRequest.of(page, size));
        return new PurchasePage(ids.stream().map(id -> postings.purchaseResponse(businessId, customerId, id)).toList(), page, size,
                count);
    }

    public List<PaymentHistoryResponse> payments(String businessId, long customerId) {
        return payments.findConfirmedByCustomer(businessId, customerId).stream().map(this::toPaymentHistory).toList();
    }

    private BigDecimal openingAt(String businessId, long customerId, LocalDate from) {
        BigDecimal amount = ledger.opening(businessId, customerId);
        AccountOpeningBalance openingRecord = openings.findByBusinessIdAndCustomerId(businessId, customerId).orElse(null);
        LocalDate cutoff = openingRecord == null ? null : openingRecord.getCutoffDate();
        BigDecimal carried = cutoff != null && cutoff.isBefore(from) ? amount : MoneyAmounts.zero();
        BigDecimal purchases = this.purchases.sumBefore(businessId, customerId, from);
        BigDecimal payments = this.payments.sumBefore(businessId, customerId, from);
        return carried.add(purchases).subtract(payments).setScale(2);
    }

    private BigDecimal openingMovement(String businessId, long customerId, LocalDate from, LocalDate to) {
        AccountOpeningBalance opening = openings.findByBusinessIdAndCustomerId(businessId, customerId).orElse(null);
        LocalDate cutoff = opening == null ? null : opening.getCutoffDate();
        if (cutoff == null || cutoff.isBefore(from) || cutoff.isAfter(to)) return MoneyAmounts.zero();
        return ledger.opening(businessId, customerId);
    }

    private LocalDate openingCutoff(String businessId, long customerId) {
        AccountOpeningBalance opening = openings.findByBusinessIdAndCustomerId(businessId, customerId).orElse(null);
        return opening == null ? null : opening.getCutoffDate();
    }

    private Instant openingTimestamp(String businessId, long customerId) {
        AccountOpeningBalance opening = openings.findByBusinessIdAndCustomerId(businessId, customerId).orElse(null);
        return opening == null ? Instant.EPOCH : opening.getPostedAt();
    }

    private PaymentHistoryResponse toPaymentHistory(Payment payment) {
        return new PaymentHistoryResponse(payment.getId(), MoneyAmounts.text(payment.getAmount()), payment.getMethod(),
                payment.getOccurredOn(), payment.getReferenceText(), payment.getPostedAt());
    }
}
